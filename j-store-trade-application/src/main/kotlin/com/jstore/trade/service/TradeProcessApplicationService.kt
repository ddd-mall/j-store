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
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.*
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.trade.domain.*

interface TradeProcessUseCase {
    fun start(command: StartTradeProcessCommand): Result<Boolean, BusinessError>

    fun recordSaleAuthorized(event: SaleAuthorizedIntegrationEvent): Result<Boolean, BusinessError>

    fun recordSaleAuthorizationFailed(
        event: SaleAuthorizationFailedIntegrationEvent
    ): Result<Boolean, BusinessError>

    fun recordInventoryReserved(
        event: InventoryReservedIntegrationEvent
    ): Result<Boolean, BusinessError>

    fun recordInventoryReservationFailed(
        event: InventoryReservationFailedIntegrationEvent
    ): Result<Boolean, BusinessError>

    fun close(event: OrderCancelledIntegrationEvent): Result<Boolean, BusinessError>

    fun markPaid(event: OrderPaidIntegrationEvent): Result<Boolean, BusinessError>
}

class TradeProcessApplicationService(
    private val processes: TradeProcessRepository,
    private val publisher: IntegrationMessagePublisher,
) : TradeProcessUseCase {
    override fun start(command: StartTradeProcessCommand): Result<Boolean, BusinessError> {
        val items = command.items.map { it.toSnapshot() }
        val id = TradeProcessId(command.orderId)
        processes.findById(id)?.let { existing ->
            return if (
                existing.matchesStartSnapshot(
                    command.orderId,
                    command.merchantId,
                    items,
                    Price.ofFen(command.payableAmountFen),
                    command.currency,
                )
            ) {
                Success(false)
            } else {
                Failure(TradeErrors.START_CONFLICT)
            }
        }

        processes.save(
            TradeProcess.start(
                id = id,
                orderId = command.orderId,
                merchantId = command.merchantId,
                items = items,
                payableAmount = Price.ofFen(command.payableAmountFen),
                currency = command.currency,
            )
        )
        publisher.publish(
            AuthorizeSaleCommand(
                orderId = command.orderId,
                merchantId = command.merchantId,
                items = command.items,
                sourceMessageId = command.messageId,
                occurredAtValue = command.occurredAtValue,
            )
        )
        return Success(true)
    }

    override fun recordSaleAuthorized(
        event: SaleAuthorizedIntegrationEvent
    ): Result<Boolean, BusinessError> =
        withProcess(event.orderId) { process ->
            when (
                val result =
                    process.recordSaleAuthorized(
                        event.items.map {
                            TradeAuthorization(it.authorizationId, it.offerId, it.expiresAt)
                        }
                    )
            ) {
                is Failure -> result
                is Success -> {
                    if (result.value) {
                        processes.save(process)
                        val authorizationByOffer = process.authorizations.associateBy { it.offerId }
                        publisher.publish(
                            ReserveInventoryCommand(
                                orderId = process.orderId,
                                merchantId = process.merchantId,
                                items =
                                    process.items.map { item ->
                                        val authorization =
                                            authorizationByOffer.getValue(item.offerId)
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
                                occurredAtValue = event.occurredAtValue,
                                acceptBefore = process.authorizations.minOf { it.expiresAt },
                            )
                        )
                    }
                    result
                }
            }
        }

    override fun recordSaleAuthorizationFailed(
        event: SaleAuthorizationFailedIntegrationEvent
    ): Result<Boolean, BusinessError> =
        failAndReject(event.orderId, event.reason, event.messageId, event.occurredAtValue)

    override fun recordInventoryReserved(
        event: InventoryReservedIntegrationEvent
    ): Result<Boolean, BusinessError> =
        withProcess(event.orderId) { process ->
            when (
                val result =
                    process.recordInventoryReserved(
                        event.reservationIds,
                        event.reservationExpiresAt,
                    )
            ) {
                is Failure -> result
                is Success -> {
                    if (result.value) {
                        processes.save(process)
                        publisher.publish(
                            TradeCommitmentConfirmedIntegrationEvent(
                                process.orderId,
                                event.messageId,
                                event.occurredAtValue,
                            )
                        )
                    }
                    result
                }
            }
        }

    override fun recordInventoryReservationFailed(
        event: InventoryReservationFailedIntegrationEvent
    ): Result<Boolean, BusinessError> =
        withProcess(event.orderId) { process ->
            when (val result = process.fail(event.reason)) {
                is Failure -> result
                is Success -> {
                    if (result.value) {
                        processes.save(process)
                        releaseAuthorizations(process, event.messageId, event.occurredAtValue)
                        publishRejection(
                            process,
                            event.reason,
                            event.messageId,
                            event.occurredAtValue,
                        )
                    }
                    result
                }
            }
        }

    override fun close(event: OrderCancelledIntegrationEvent): Result<Boolean, BusinessError> =
        withProcess(event.orderId) { process ->
            val previousStatus = process.status
            when (val result = process.close(event.reason)) {
                is Failure -> result
                is Success -> {
                    if (result.value) {
                        processes.save(process)
                        if (previousStatus == TradeProcessStatus.COMMITTED) {
                            publisher.publish(
                                ReleaseInventoryCommand(
                                    process.orderId,
                                    process.items.map { ContractItem(it.skuId, it.quantity) },
                                    event.messageId,
                                    event.occurredAtValue,
                                )
                            )
                        }
                        if (
                            previousStatus in
                                setOf(TradeProcessStatus.RESERVING, TradeProcessStatus.COMMITTED)
                        ) {
                            releaseAuthorizations(process, event.messageId, event.occurredAtValue)
                        }
                    }
                    result
                }
            }
        }

    override fun markPaid(event: OrderPaidIntegrationEvent): Result<Boolean, BusinessError> =
        withProcess(event.orderId) { process ->
            when (val result = process.markPaid()) {
                is Failure -> result
                is Success -> {
                    if (result.value) processes.save(process)
                    result
                }
            }
        }

    private fun failAndReject(
        orderId: Long,
        reason: String,
        sourceMessageId: String,
        occurredAt: java.time.Instant,
    ): Result<Boolean, BusinessError> =
        withProcess(orderId) { process ->
            when (val result = process.fail(reason)) {
                is Failure -> result
                is Success -> {
                    if (result.value) {
                        processes.save(process)
                        publishRejection(process, reason, sourceMessageId, occurredAt)
                    }
                    result
                }
            }
        }

    private fun publishRejection(
        process: TradeProcess,
        reason: String,
        sourceMessageId: String,
        occurredAt: java.time.Instant,
    ) {
        publisher.publish(
            TradeCommitmentFailedIntegrationEvent(
                process.orderId,
                reason,
                sourceMessageId,
                occurredAt,
            )
        )
    }

    private fun releaseAuthorizations(
        process: TradeProcess,
        sourceMessageId: String,
        occurredAt: java.time.Instant,
    ) {
        if (process.authorizations.isNotEmpty()) {
            publisher.publish(
                ReleaseSaleAuthorizationCommand(
                    process.orderId,
                    process.authorizations.map { it.authorizationId },
                    sourceMessageId,
                    occurredAt,
                )
            )
        }
    }

    private inline fun withProcess(
        orderId: Long,
        action: (TradeProcess) -> Result<Boolean, BusinessError>,
    ): Result<Boolean, BusinessError> {
        val process =
            processes.findById(TradeProcessId(orderId)) ?: return Failure(TradeErrors.NOT_FOUND)
        return action(process)
    }
}

private fun ContractSaleItem.toSnapshot() =
    TradeItemSnapshot(
        offerId,
        storeId,
        spuId,
        skuId,
        quantity,
        catalogSnapshotVersion,
        offerVersion,
        fulfillmentNodeId,
        channelId,
        Price.ofFen(unitPriceFen),
    )
