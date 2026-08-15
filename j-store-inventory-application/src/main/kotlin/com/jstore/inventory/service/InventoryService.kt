/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.inventory.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.contracts.commerce.ConfirmInventoryCommand
import com.jstore.contracts.commerce.PhysicalStockChangedIntegrationEvent
import com.jstore.contracts.commerce.ReleaseInventoryCommand
import com.jstore.contracts.commerce.ReserveInventoryCommand
import com.jstore.inventory.domain.FulfillmentNodeId
import com.jstore.inventory.domain.InventoryErrors
import com.jstore.inventory.domain.SkuId
import com.jstore.inventory.domain.StockPosition
import com.jstore.inventory.domain.StockPositionGuard
import com.jstore.inventory.domain.StockPositionId
import com.jstore.inventory.domain.StockPositionRepository
import com.jstore.inventory.domain.StockReservation
import com.jstore.inventory.domain.StockReservationId
import com.jstore.inventory.domain.StockReservationRepository
import com.jstore.inventory.domain.event.StockReservationFailedEvent
import com.jstore.inventory.domain.event.StockReservedEvent
import com.jstore.messaging.IntegrationMessageHandler
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class StockReservationResult(
    val authorizationIds: List<String>,
    val reservationIds: List<String>,
    val expiresAt: Instant,
)

interface InventoryUseCase {
    fun reserve(command: ReserveInventoryCommand): Result<StockReservationResult, BusinessError>

    fun confirm(tradeId: Long, orderPlanId: Long): Result<Unit, BusinessError>

    fun release(tradeId: Long, orderPlanId: Long): Result<Unit, BusinessError>

    fun applyPhysicalStock(
        message: PhysicalStockChangedIntegrationEvent
    ): Result<Boolean, BusinessError>
}

class InventoryService(
    private val positionGuard: StockPositionGuard,
    private val positions: StockPositionRepository,
    private val reservations: StockReservationRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val reservationTtl: Duration = Duration.ofMinutes(30),
) : InventoryUseCase {
    override fun reserve(
        command: ReserveInventoryCommand
    ): Result<StockReservationResult, BusinessError> {
        if (command.items.isEmpty()) return Failure(InventoryErrors.INVALID_QUANTITY)
        val receivedAt = Instant.now(clock)
        if (hasExpired(command, receivedAt)) {
            return Failure(InventoryErrors.RESERVATION_CONFLICT)
        }
        if (command.items.any { it.authorizationId.isBlank() || it.quantity <= 0 }) {
            return Failure(InventoryErrors.RESERVATION_CONFLICT)
        }

        val normalized =
            command.items
                .groupBy { Triple(it.skuId, it.fulfillmentNodeId, it.authorizationId) }
                .map { (key, lines) ->
                    val first = lines.first()
                    first.copy(quantity = lines.sumOf { it.quantity })
                }
                .sortedWith(compareBy({ it.skuId }, { it.fulfillmentNodeId }))
        val existing = reservations.findByOrderPlanId(command.orderPlanId)
        if (existing.isNotEmpty()) {
            return Success(
                StockReservationResult(
                    existing.map { it.saleAuthorizationId }.distinct(),
                    existing.map { it.id.value },
                    existing.minOf { it.expiresAt },
                )
            )
        }

        val keys = normalized.map {
            StockPositionId("${it.skuId}@${it.fulfillmentNodeId}")
        }
        val locked = positionGuard.lock(keys).associateBy { it.id }
        if (locked.size != keys.distinct().size) return Failure(InventoryErrors.POSITION_NOT_FOUND)
        val lockedAt = Instant.now(clock)
        if (hasExpired(command, lockedAt)) {
            return Failure(InventoryErrors.RESERVATION_CONFLICT)
        }

        val created = mutableListOf<StockReservation>()
        for (item in normalized) {
            val key = StockPositionId("${item.skuId}@${item.fulfillmentNodeId}")
            val position = locked.getValue(key)
            position.reserve(item.quantity).onFailure {
                return Failure(it)
            }
            val businessKey =
                "TRADE-${command.tradeId}-PLAN-${command.orderPlanId}-SKU-${item.skuId}-NODE-${item.fulfillmentNodeId}"
            val reservation =
                StockReservation(
                    id = StockReservationId(businessKey),
                    businessKey = businessKey,
                    tradeId = command.tradeId,
                    orderPlanId = command.orderPlanId,
                    saleAuthorizationId = item.authorizationId,
                    skuId = SkuId(item.skuId),
                    fulfillmentNodeId = FulfillmentNodeId(item.fulfillmentNodeId),
                    quantity = item.quantity,
                    expiresAt = minOf(lockedAt.plus(reservationTtl), item.expiresAt),
                )
            positions.save(position)
            reservations.save(reservation)
            created += reservation
        }
        return Success(
            StockReservationResult(
                created.map { it.saleAuthorizationId }.distinct(),
                created.map { it.id.value },
                created.minOf { it.expiresAt },
            )
        )
    }

    private fun hasExpired(command: ReserveInventoryCommand, now: Instant): Boolean =
        command.acceptBefore?.let { !now.isBefore(it) } == true ||
            command.items.any { !now.isBefore(it.expiresAt) }

    override fun confirm(tradeId: Long, orderPlanId: Long): Result<Unit, BusinessError> {
        val records = reservations.findByOrderPlanId(orderPlanId)
        if (records.any { it.tradeId != tradeId })
            return Failure(InventoryErrors.RESERVATION_NOT_FOUND)
        if (records.isEmpty()) return Failure(InventoryErrors.RESERVATION_NOT_FOUND)
        val keys = records.map {
            StockPositionId("${it.skuId.value}@${it.fulfillmentNodeId.value}")
        }
        val locked = positionGuard.lock(keys).associateBy { it.id }
        for (record in records) {
            val key = StockPositionId("${record.skuId.value}@${record.fulfillmentNodeId.value}")
            val position = locked[key] ?: return Failure(InventoryErrors.POSITION_NOT_FOUND)
            if (record.status.name == "CONFIRMED") continue
            position.confirm(record.quantity).onFailure {
                return Failure(it)
            }
            record.confirm().onFailure {
                return Failure(it)
            }
            positions.save(position)
            reservations.save(record)
        }
        return Success(Unit)
    }

    override fun release(tradeId: Long, orderPlanId: Long): Result<Unit, BusinessError> {
        val records = reservations.findByOrderPlanId(orderPlanId)
        if (records.any { it.tradeId != tradeId })
            return Failure(InventoryErrors.RESERVATION_NOT_FOUND)
        if (records.isEmpty()) return Success(Unit)
        val active = records.filter { it.status.name == "RESERVED" }
        val keys = active.map { StockPositionId("${it.skuId.value}@${it.fulfillmentNodeId.value}") }
        val locked = positionGuard.lock(keys).associateBy { it.id }
        for (record in active) {
            val key = StockPositionId("${record.skuId.value}@${record.fulfillmentNodeId.value}")
            val position = locked[key] ?: return Failure(InventoryErrors.POSITION_NOT_FOUND)
            position.release(record.quantity).onFailure {
                return Failure(it)
            }
            record.release(Instant.now(clock)).onFailure {
                return Failure(it)
            }
            positions.save(position)
            reservations.save(record)
        }
        return Success(Unit)
    }

    override fun applyPhysicalStock(
        message: PhysicalStockChangedIntegrationEvent
    ): Result<Boolean, BusinessError> {
        val id = StockPositionId("${message.skuId}@${message.fulfillmentNodeId}")
        val locked = positionGuard.lock(listOf(id)).singleOrNull()
        val position =
            locked
                ?: StockPosition(
                    id,
                    SkuId(message.skuId),
                    FulfillmentNodeId(message.fulfillmentNodeId),
                    onHand = 0,
                )
        val changed = position.applyPhysicalStock(message.onHand, message.sourceVersion)
        if (changed) positions.save(position)
        return Success(changed)
    }
}

class ReserveInventoryCommandHandler(
    private val useCase: InventoryUseCase,
    private val publisher: DomainEventPublisher,
) : IntegrationMessageHandler<ReserveInventoryCommand> {
    override fun handlerId() = "inventory.reserve-authorized-stock.v1"

    override fun handle(message: ReserveInventoryCommand) {
        when (val result = useCase.reserve(message)) {
            is Success ->
                publisher.publishEvent(
                    StockReservedEvent(
                        tradeId = message.tradeId,
                        orderPlanId = message.orderPlanId,
                        authorizationIds = result.value.authorizationIds,
                        reservationIds = result.value.reservationIds,
                        reservationExpiresAt = result.value.expiresAt,
                    )
                )
            is Failure ->
                publisher.publishEvent(
                    StockReservationFailedEvent(
                        message.tradeId,
                        message.orderPlanId,
                        message.items.map { it.authorizationId }.distinct(),
                        result.error.message,
                    )
                )
        }
    }
}

class ConfirmInventoryCommandHandler(private val useCase: InventoryUseCase) :
    IntegrationMessageHandler<ConfirmInventoryCommand> {
    override fun handlerId() = "inventory.confirm-reservation.v2"

    override fun handle(message: ConfirmInventoryCommand) {
        useCase.confirm(message.tradeId, message.orderPlanId).onFailure {
            throw IllegalStateException(it.message)
        }
    }
}

class ReleaseInventoryCommandHandler(private val useCase: InventoryUseCase) :
    IntegrationMessageHandler<ReleaseInventoryCommand> {
    override fun handlerId() = "inventory.release-reservation.v2"

    override fun handle(message: ReleaseInventoryCommand) {
        useCase.release(message.tradeId, message.orderPlanId).onFailure {
            throw IllegalStateException(it.message)
        }
    }
}

class PhysicalStockChangedHandler(private val useCase: InventoryUseCase) :
    IntegrationMessageHandler<PhysicalStockChangedIntegrationEvent> {
    override fun handlerId() = "inventory.apply-wms-physical-stock.v1"

    override fun handle(message: PhysicalStockChangedIntegrationEvent) {
        useCase.applyPhysicalStock(message).onFailure { throw IllegalStateException(it.message) }
    }
}
