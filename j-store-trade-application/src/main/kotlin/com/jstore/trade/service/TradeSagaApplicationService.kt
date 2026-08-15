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
package com.jstore.trade.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.contracts.commerce.*
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.trade.domain.*

fun interface TradeOrderCreationGateway {
    fun createOrder(trade: Trade, plan: TradeOrderPlan): Result<Long, BusinessError>

    fun cancelOrder(plan: TradeOrderPlan, reason: String): Result<Unit, BusinessError> =
        Success(Unit)
}

fun interface TradeSettlementGateway {
    fun prepareSettlement(
        trade: Trade,
        settlementPlanId: SettlementPlanId,
    ): Result<Unit, BusinessError>

    fun cancelSettlement(
        trade: Trade,
        settlementPlanId: SettlementPlanId,
        reason: String,
    ): Result<Unit, BusinessError> = Success(Unit)
}

interface TradeSagaUseCase {
    fun recordSaleAuthorized(event: SaleAuthorizedIntegrationEvent): Result<Boolean, BusinessError>

    fun recordInventoryReserved(
        event: InventoryReservedIntegrationEvent
    ): Result<Boolean, BusinessError>

    fun recordSaleAuthorizationFailed(
        event: SaleAuthorizationFailedIntegrationEvent
    ): Result<Boolean, BusinessError>

    fun recordInventoryReservationFailed(
        event: InventoryReservationFailedIntegrationEvent
    ): Result<Boolean, BusinessError>

    fun recordOrderCancelled(event: OrderCancelledIntegrationEvent): Result<Boolean, BusinessError>
}

class TradeSagaApplicationService(
    private val trades: TradeRepository,
    private val ids: TradeIdentityGenerator,
    private val orders: TradeOrderCreationGateway,
    private val settlement: TradeSettlementGateway,
    private val publisher: IntegrationMessagePublisher,
) : TradeSagaUseCase {
    override fun recordSaleAuthorized(
        event: SaleAuthorizedIntegrationEvent
    ): Result<Boolean, BusinessError> =
        withPlan(event.tradeId, event.orderPlanId) { trade, plan ->
            if (trade.status == TradeStatus.FAILED) {
                val authorizationIds = event.items.map { it.authorizationId }
                if (
                    authorizationIds.any { it.isBlank() } ||
                        event.items.map { it.offerId }.toSet() !=
                            plan.items.map { it.offerId }.toSet()
                ) {
                    return@withPlan Failure(TradeErrors.INVALID_AUTHORIZATION)
                }
                publishSaleAuthorizationRelease(
                    trade,
                    plan,
                    authorizationIds,
                    event.messageId,
                    event.occurredAt,
                )
                return@withPlan Success(false)
            }
            val changed =
                trade.recordSaleAuthorized(
                    plan.id,
                    event.items.map {
                        TradeAuthorization(it.authorizationId, it.offerId, it.expiresAt)
                    },
                )
            if (changed is Success && changed.value) {
                trades.save(trade)
                val authorizationByOffer = plan.authorizations.associateBy { it.offerId }
                publisher.publish(
                    ReserveInventoryCommand(
                        tradeId = trade.id.value,
                        orderPlanId = plan.id.value,
                        items =
                            plan.items.map { item ->
                                val authorization = authorizationByOffer.getValue(item.offerId)
                                ContractAuthorizedSaleItem(
                                    authorization.authorizationId,
                                    item.offerId,
                                    item.skuId,
                                    item.quantity,
                                    item.fulfillmentNodeId,
                                    authorization.expiresAt,
                                )
                            },
                        sourceMessageId = event.messageId,
                        merchantId = plan.merchantId,
                        occurredAtValue = event.occurredAt,
                        acceptBefore = plan.authorizations.minOf { it.expiresAt },
                    )
                )
            }
            changed
        }

    override fun recordInventoryReserved(
        event: InventoryReservedIntegrationEvent
    ): Result<Boolean, BusinessError> =
        withPlan(event.tradeId, event.orderPlanId) { trade, plan ->
            if (trade.status == TradeStatus.FAILED) {
                if (event.reservationIds.isEmpty()) {
                    return@withPlan Failure(TradeErrors.INVALID_RESERVATION)
                }
                publishInventoryRelease(trade, plan, event.messageId, event.occurredAt)
                if (event.authorizationIds.isNotEmpty()) {
                    publishSaleAuthorizationRelease(
                        trade,
                        plan,
                        event.authorizationIds,
                        event.messageId,
                        event.occurredAt,
                    )
                }
                return@withPlan Success(false)
            }
            val changed =
                trade.recordInventoryReserved(
                    plan.id,
                    event.reservationIds,
                    event.reservationExpiresAt,
                )
            changed.onFailure {
                return@withPlan Failure(it)
            }
            if (changed is Success && changed.value) trades.save(trade)
            if (trade.orderPlans.all { it.status == TradeOrderPlanStatus.RESERVED }) {
                trade.startOrderCreation().onFailure {
                    return@withPlan Failure(it)
                }
                trades.save(trade)
                for (orderPlan in trade.orderPlans) {
                    val orderId =
                        when (val created = orders.createOrder(trade, orderPlan)) {
                            is Failure -> {
                                trade.orderPlans
                                    .mapNotNull { it.orderId?.let { id -> it to id } }
                                    .forEach { (createdPlan, _) ->
                                        orders
                                            .cancelOrder(createdPlan, created.error.message)
                                            .onFailure {
                                                return@withPlan Failure(it)
                                            }
                                    }
                                return@withPlan failAndCompensate(
                                    trade,
                                    orderPlan,
                                    created.error.message,
                                    event.messageId,
                                    event.occurredAt,
                                )
                            }
                            is Success -> created.value
                        }
                    trade.recordOrderCreated(orderPlan.id, orderId).onFailure {
                        return@withPlan Failure(it)
                    }
                }
                val settlementPlanId = SettlementPlanId(ids.nextId())
                trade.prepareSettlement(settlementPlanId).onFailure {
                    return@withPlan Failure(it)
                }
                trades.save(trade)
                settlement.prepareSettlement(trade, settlementPlanId).onFailure {
                    return@withPlan Failure(it)
                }
            }
            changed
        }

    override fun recordSaleAuthorizationFailed(
        event: SaleAuthorizationFailedIntegrationEvent
    ): Result<Boolean, BusinessError> =
        withPlan(event.tradeId, event.orderPlanId) { trade, plan ->
            failAndCompensate(trade, plan, event.reason, event.messageId, event.occurredAt)
        }

    override fun recordInventoryReservationFailed(
        event: InventoryReservationFailedIntegrationEvent
    ): Result<Boolean, BusinessError> =
        withPlan(event.tradeId, event.orderPlanId) { trade, plan ->
            failAndCompensate(trade, plan, event.reason, event.messageId, event.occurredAt)
        }

    override fun recordOrderCancelled(
        event: OrderCancelledIntegrationEvent
    ): Result<Boolean, BusinessError> =
        withPlan(event.tradeId, event.orderPlanId) { trade, plan ->
            if (trade.status == TradeStatus.FAILED) return@withPlan Success(false)
            trade.settlementPlanId?.let { settlementPlanId ->
                settlement.cancelSettlement(trade, settlementPlanId, event.reason).onFailure {
                    return@withPlan Failure(it)
                }
            }
            trade.orderPlans
                .filter { it.orderId != null && it.orderId != event.orderId }
                .forEach { createdPlan ->
                    orders.cancelOrder(createdPlan, event.reason).onFailure {
                        return@withPlan Failure(it)
                    }
                }
            val changed = trade.recordOrderCancelled(plan.id, event.orderId, event.reason)
            changed.onFailure {
                return@withPlan Failure(it)
            }
            if (changed is Success && changed.value) {
                trades.save(trade)
                publishCompensations(trade, event.messageId, event.occurredAt)
            }
            changed
        }

    private fun failAndCompensate(
        trade: Trade,
        failedPlan: TradeOrderPlan,
        reason: String,
        sourceMessageId: String,
        occurredAt: java.time.Instant,
    ): Result<Boolean, BusinessError> {
        if (trade.status == TradeStatus.FAILED) {
            publishCompensations(trade, sourceMessageId, occurredAt)
            return Success(false)
        }
        val changed = trade.fail(failedPlan.id, reason)
        changed.onFailure {
            return Failure(it)
        }
        if (changed is Success && !changed.value) return changed
        trades.save(trade)
        publishCompensations(trade, sourceMessageId, occurredAt)
        return changed
    }

    private fun publishCompensations(
        trade: Trade,
        sourceMessageId: String,
        occurredAt: java.time.Instant,
    ) {
        trade.orderPlans.forEach { plan ->
            // An authorization means the inventory command may already be in flight. Releasing an
            // absent reservation is intentionally idempotent and closes that race safely.
            if (plan.authorizations.isNotEmpty() || plan.reservationIds.isNotEmpty()) {
                publishInventoryRelease(trade, plan, sourceMessageId, occurredAt)
            }
            if (plan.authorizations.isNotEmpty()) {
                publishSaleAuthorizationRelease(
                    trade,
                    plan,
                    plan.authorizations.map { it.authorizationId },
                    sourceMessageId,
                    occurredAt,
                )
            }
        }
    }

    private fun publishInventoryRelease(
        trade: Trade,
        plan: TradeOrderPlan,
        sourceMessageId: String,
        occurredAt: java.time.Instant,
    ) =
        publisher.publish(
            ReleaseInventoryCommand(
                trade.id.value,
                plan.id.value,
                plan.items.map { ContractItem(it.skuId, it.quantity) },
                sourceMessageId,
                occurredAt,
            )
        )

    private fun publishSaleAuthorizationRelease(
        trade: Trade,
        plan: TradeOrderPlan,
        authorizationIds: List<String>,
        sourceMessageId: String,
        occurredAt: java.time.Instant,
    ) =
        publisher.publish(
            ReleaseSaleAuthorizationCommand(
                trade.id.value,
                plan.id.value,
                authorizationIds.distinct(),
                sourceMessageId,
                occurredAt,
            )
        )

    private fun withPlan(
        rawTradeId: Long,
        rawPlanId: Long,
        block: (Trade, TradeOrderPlan) -> Result<Boolean, BusinessError>,
    ): Result<Boolean, BusinessError> {
        val planId = TradeOrderPlanId(rawPlanId)
        val trade = trades.findById(TradeId(rawTradeId)) ?: return Failure(TradeErrors.NOT_FOUND)
        if (trade.orderPlans.none { it.id == planId }) return Failure(TradeErrors.NOT_FOUND)
        return block(trade, trade.plan(planId))
    }
}
