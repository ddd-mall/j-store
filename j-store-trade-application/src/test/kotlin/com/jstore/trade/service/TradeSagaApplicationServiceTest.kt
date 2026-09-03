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

import com.jstore.common.geo.AddressComponent
import com.jstore.common.geo.CountryCode
import com.jstore.common.geo.DivisionLevel
import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.ContractAuthorizedSaleItem
import com.jstore.contracts.commerce.InventoryReservationFailedIntegrationEvent
import com.jstore.contracts.commerce.InventoryReservedIntegrationEvent
import com.jstore.contracts.commerce.OrderCancelledIntegrationEvent
import com.jstore.contracts.commerce.OrderCreatedFromTradeIntegrationEvent
import com.jstore.contracts.commerce.OrderCreationRejectedFromTradeIntegrationEvent
import com.jstore.contracts.commerce.PaymentCancellationConfirmedIntegrationEvent
import com.jstore.contracts.commerce.PaymentPreparedIntegrationEvent
import com.jstore.contracts.commerce.ReleaseInventoryCommand
import com.jstore.contracts.commerce.ReleaseSaleAuthorizationCommand
import com.jstore.contracts.commerce.SaleAuthorizedIntegrationEvent
import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.trade.domain.*
import java.time.Instant
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TradeSagaApplicationServiceTest {
    @Test
    fun `orders and settlement are created only after every inventory plan is reserved`() {
        val repository = SagaTradeRepository(trade())
        val messages = CapturingSagaPublisher()
        val orders = CapturingOrderGateway()
        var preparedSettlement: Long? = null
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                orders,
                { _, id -> Success(Unit).also { preparedSettlement = id.value } },
                messages,
            )
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")

        assertIs<Success<Boolean>>(
            service.recordSaleAuthorized(
                SaleAuthorizedIntegrationEvent(
                    9001,
                    9101,
                    listOf(ContractAuthorizedSaleItem("A-1", 11, 101, 1, "NODE-1", expiresAt)),
                    "sale-event",
                    Instant.parse("2029-01-01T00:00:00Z"),
                )
            )
        )
        assertEquals(0, orders.creationRequests.size)
        assertNull(preparedSettlement)

        assertIs<Success<Boolean>>(
            service.recordInventoryReserved(
                InventoryReservedIntegrationEvent(
                    9001,
                    9101,
                    listOf("A-1"),
                    listOf("R-1"),
                    expiresAt.minusSeconds(60),
                    "inventory-event",
                    Instant.parse("2029-01-01T00:01:00Z"),
                )
            )
        )

        assertEquals(1, orders.creationRequests.size)
        assertNull(preparedSettlement)
        assertEquals(TradeStatus.CREATING_ORDERS, repository.trade.status)
        assertNull(repository.trade.orderPlans.single().orderId)

        service.recordOrderCreated(
            OrderCreatedFromTradeIntegrationEvent(
                9001,
                9101,
                7001,
                "order-created",
                Instant.parse("2029-01-01T00:01:30Z"),
            )
        )
        assertEquals(9901, preparedSettlement)
        assertEquals(TradeStatus.SETTLEMENT_PREPARING, repository.trade.status)
        assertEquals(7001, repository.trade.orderPlans.single().orderId)

        assertIs<Success<Boolean>>(
            service.recordPaymentPrepared(
                PaymentPreparedIntegrationEvent(
                    9001,
                    9901,
                    "FULL",
                    8001,
                    1000,
                    "CNY",
                    expiresAt.minusSeconds(60),
                    expiresAt,
                    "payment-command",
                    Instant.parse("2029-01-01T00:02:00Z"),
                )
            )
        )
        assertEquals(TradeStatus.PAYMENT_READY, repository.trade.status)
        assertEquals(8001, repository.trade.paymentIdFor("FULL"))
    }

    @Test
    fun `insufficient payment window fails trade and compensates created orders and reservations`() {
        val repository = SagaTradeRepository(trade())
        val messages = CapturingSagaPublisher()
        val orders = CapturingOrderGateway()
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                orders,
                { _, _ -> Failure(TradeErrors.RESERVATION_WINDOW_INSUFFICIENT) },
                messages,
            )
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
        service.recordSaleAuthorized(
            SaleAuthorizedIntegrationEvent(
                9001,
                9101,
                listOf(ContractAuthorizedSaleItem("A-1", 11, 101, 1, "NODE-1", expiresAt)),
                "sale-event",
                Instant.parse("2029-01-01T00:00:00Z"),
            )
        )
        service.recordInventoryReserved(
            InventoryReservedIntegrationEvent(
                9001,
                9101,
                listOf("A-1"),
                listOf("R-1"),
                expiresAt.minusSeconds(60),
                "inventory-event",
                Instant.parse("2029-01-01T00:01:00Z"),
            )
        )

        val result =
            service.recordOrderCreated(
                OrderCreatedFromTradeIntegrationEvent(
                    9001,
                    9101,
                    7001,
                    "order-created",
                    Instant.parse("2029-01-01T00:01:30Z"),
                )
            )

        assertEquals(true, assertIs<Success<Boolean>>(result).value)
        assertEquals(TradeStatus.FAILED, repository.trade.status)
        assertEquals(listOf(9101L), orders.cancellationRequests)
        assertEquals(1, messages.messages.filterIsInstance<ReleaseInventoryCommand>().size)
        assertEquals(1, messages.messages.filterIsInstance<ReleaseSaleAuthorizationCommand>().size)
    }

    @Test
    fun `inventory failure persists terminal failure and releases sale authorization`() {
        val repository = SagaTradeRepository(trade())
        val messages = CapturingSagaPublisher()
        val orders = CapturingOrderGateway()
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                orders,
                { _, _ -> Success(Unit) },
                messages,
            )
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
        service.recordSaleAuthorized(
            SaleAuthorizedIntegrationEvent(
                9001,
                9101,
                listOf(ContractAuthorizedSaleItem("A-1", 11, 101, 1, "NODE-1", expiresAt)),
                "sale-event",
                Instant.parse("2029-01-01T00:00:00Z"),
            )
        )

        val result =
            service.recordInventoryReservationFailed(
                InventoryReservationFailedIntegrationEvent(
                    9001,
                    9101,
                    listOf("A-1"),
                    "out of stock",
                    "inventory-failed",
                    Instant.parse("2029-01-01T00:01:00Z"),
                )
            )

        assertEquals(true, assertIs<Success<Boolean>>(result).value)
        assertEquals(TradeStatus.FAILED, repository.trade.status)
        assertEquals("out of stock", repository.trade.failureReason)
        assertEquals(0, orders.creationRequests.size)
        assertEquals(1, messages.messages.filterIsInstance<ReleaseInventoryCommand>().size)
        assertEquals(1, messages.messages.filterIsInstance<ReleaseSaleAuthorizationCommand>().size)
    }

    @Test
    fun `late inventory success after another plan failed is compensated`() {
        val repository = SagaTradeRepository(trade(planCount = 2))
        val messages = CapturingSagaPublisher()
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                CapturingOrderGateway(),
                { _, _ -> Success(Unit) },
                messages,
            )
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
        repository.trade.orderPlans.forEachIndexed { index, plan ->
            service.recordSaleAuthorized(
                SaleAuthorizedIntegrationEvent(
                    9001,
                    plan.id.value,
                    listOf(
                        ContractAuthorizedSaleItem(
                            "A-${index + 1}",
                            plan.items.single().offerId,
                            plan.items.single().skuId,
                            1,
                            plan.items.single().fulfillmentNodeId,
                            expiresAt,
                        )
                    ),
                    "sale-event-${index + 1}",
                    Instant.parse("2029-01-01T00:00:00Z"),
                )
            )
        }
        service.recordInventoryReservationFailed(
            InventoryReservationFailedIntegrationEvent(
                9001,
                9101,
                listOf("A-1"),
                "out of stock",
                "inventory-failed",
                Instant.parse("2029-01-01T00:01:00Z"),
            )
        )
        val releasesBeforeLateResult =
            messages.messages.filterIsInstance<ReleaseInventoryCommand>().size

        val lateEvent =
            InventoryReservedIntegrationEvent(
                9001,
                9102,
                listOf("A-2"),
                listOf("R-2"),
                expiresAt.minusSeconds(60),
                "late-inventory-event",
                Instant.parse("2029-01-01T00:02:00Z"),
            )
        val result = service.recordInventoryReserved(lateEvent)

        assertEquals(false, assertIs<Success<Boolean>>(result).value)
        assertEquals(TradeStatus.FAILED, repository.trade.status)
        assertEquals(
            releasesBeforeLateResult + 1,
            messages.messages.filterIsInstance<ReleaseInventoryCommand>().size,
        )
        assertEquals(
            1,
            messages.messages.filterIsInstance<ReleaseInventoryCommand>().count {
                it.orderPlanId == 9102L && it.sourceMessageId == lateEvent.messageId
            },
        )
    }

    @Test
    fun `late sale authorization after another plan failed is released`() {
        val repository = SagaTradeRepository(trade(planCount = 2))
        val messages = CapturingSagaPublisher()
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                CapturingOrderGateway(),
                { _, _ -> Success(Unit) },
                messages,
            )
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
        service.recordSaleAuthorizationFailed(
            com.jstore.contracts.commerce.SaleAuthorizationFailedIntegrationEvent(
                9001,
                9101,
                "offer closed",
                "sale-failed",
                Instant.parse("2029-01-01T00:01:00Z"),
            )
        )

        val lateEvent =
            SaleAuthorizedIntegrationEvent(
                9001,
                9102,
                listOf(ContractAuthorizedSaleItem("A-2", 12, 102, 1, "NODE-2", expiresAt)),
                "late-sale-event",
                Instant.parse("2029-01-01T00:02:00Z"),
            )
        val result = service.recordSaleAuthorized(lateEvent)

        assertEquals(false, assertIs<Success<Boolean>>(result).value)
        assertEquals(TradeStatus.FAILED, repository.trade.status)
        assertEquals(
            1,
            messages.messages.filterIsInstance<ReleaseSaleAuthorizationCommand>().count {
                it.orderPlanId == 9102L && it.sourceMessageId == lateEvent.messageId
            },
        )
    }

    @Test
    fun `buyer cancellation waits for safe payment cancellation before releasing commitments`() {
        val repository = SagaTradeRepository(trade(planCount = 2))
        val messages = CapturingSagaPublisher()
        val orders = CapturingOrderGateway()
        var cancelledSettlement: Long? = null
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                orders,
                object : TradeSettlementGateway {
                    override fun prepareSettlement(
                        trade: Trade,
                        settlementPlanId: SettlementPlanId,
                    ) = Success(Unit)

                    override fun cancelSettlement(
                        trade: Trade,
                        settlementPlanId: SettlementPlanId,
                        reason: String,
                    ) = Success(Unit).also { cancelledSettlement = settlementPlanId.value }
                },
                messages,
            )
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
        repository.trade.orderPlans.forEachIndexed { index, plan ->
            val authorizationId = "A-${index + 1}"
            service.recordSaleAuthorized(
                SaleAuthorizedIntegrationEvent(
                    9001,
                    plan.id.value,
                    listOf(
                        ContractAuthorizedSaleItem(
                            authorizationId,
                            plan.items.single().offerId,
                            plan.items.single().skuId,
                            1,
                            plan.items.single().fulfillmentNodeId,
                            expiresAt,
                        )
                    ),
                    "sale-event-${index + 1}",
                    Instant.parse("2029-01-01T00:00:00Z"),
                )
            )
            service.recordInventoryReserved(
                InventoryReservedIntegrationEvent(
                    9001,
                    plan.id.value,
                    listOf(authorizationId),
                    listOf("R-${index + 1}"),
                    expiresAt.minusSeconds(60),
                    "inventory-event-${index + 1}",
                    Instant.parse("2029-01-01T00:01:00Z"),
                )
            )
        }
        repository.trade.orderPlans.forEachIndexed { index, plan ->
            service.recordOrderCreated(
                OrderCreatedFromTradeIntegrationEvent(
                    9001,
                    plan.id.value,
                    7001L + index,
                    "order-created-${index + 1}",
                    Instant.parse("2029-01-01T00:01:30Z"),
                )
            )
        }

        val result =
            service.recordOrderCancelled(
                OrderCancelledIntegrationEvent(
                    9001,
                    9101,
                    7001,
                    "buyer changed mind",
                    "cancel-event",
                    Instant.parse("2029-01-01T00:02:00Z"),
                )
            )

        assertEquals(true, assertIs<Success<Boolean>>(result).value)
        assertEquals(TradeStatus.CLOSING, repository.trade.status)
        assertEquals(emptyList(), orders.cancellationRequests)
        assertEquals(9901L, cancelledSettlement)
        assertEquals(
            listOf(TradeOrderPlanStatus.ORDER_CREATED, TradeOrderPlanStatus.ORDER_CREATED),
            repository.trade.orderPlans.map { it.status },
        )
        assertEquals(0, messages.messages.filterIsInstance<ReleaseInventoryCommand>().size)
        assertEquals(0, messages.messages.filterIsInstance<ReleaseSaleAuthorizationCommand>().size)

        service.recordPaymentCancellationConfirmed(
            PaymentCancellationConfirmedIntegrationEvent(
                9001,
                9901,
                "FULL",
                8001,
                "provider confirmed payment was not accepted",
                "payment-cancel-confirmed",
                Instant.parse("2029-01-01T00:03:00Z"),
            )
        )

        assertEquals(TradeStatus.FAILED, repository.trade.status)
        assertEquals(listOf(9101L, 9102L), orders.cancellationRequests)
        assertEquals(2, messages.messages.filterIsInstance<ReleaseInventoryCommand>().size)
        assertEquals(2, messages.messages.filterIsInstance<ReleaseSaleAuthorizationCommand>().size)
    }

    @Test
    fun `order business rejection fails trade and compensates reserved resources`() {
        val repository = SagaTradeRepository(trade())
        val messages = CapturingSagaPublisher()
        val orders = CapturingOrderGateway()
        var settlementPrepared = false
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                orders,
                { _, _ -> Success(Unit).also { settlementPrepared = true } },
                messages,
            )
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
        service.recordSaleAuthorized(
            SaleAuthorizedIntegrationEvent(
                9001,
                9101,
                listOf(ContractAuthorizedSaleItem("A-1", 11, 101, 1, "NODE-1", expiresAt)),
                "sale-event",
                Instant.parse("2029-01-01T00:00:00Z"),
            )
        )

        service.recordInventoryReserved(
            InventoryReservedIntegrationEvent(
                9001,
                9101,
                listOf("A-1"),
                listOf("R-1"),
                expiresAt.minusSeconds(60),
                "inventory-event",
                Instant.parse("2029-01-01T00:01:00Z"),
            )
        )
        val result =
            service.recordOrderCreationRejected(
                OrderCreationRejectedFromTradeIntegrationEvent(
                    9001,
                    9101,
                    "order rejected",
                    "order-rejected",
                    Instant.parse("2029-01-01T00:01:30Z"),
                )
            )

        assertEquals(true, assertIs<Success<Boolean>>(result).value)
        assertEquals(TradeStatus.FAILED, repository.trade.status)
        assertEquals(false, settlementPrepared)
        assertEquals(1, messages.messages.filterIsInstance<ReleaseInventoryCommand>().size)
        assertEquals(1, messages.messages.filterIsInstance<ReleaseSaleAuthorizationCommand>().size)
    }

    @Test
    fun `later order rejection cancels orders already created for the same trade`() {
        val repository = SagaTradeRepository(trade(planCount = 2))
        val messages = CapturingSagaPublisher()
        val orders = CapturingOrderGateway()
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                orders,
                { _, _ -> Success(Unit) },
                messages,
            )
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")

        repository.trade.orderPlans.forEachIndexed { index, plan ->
            val authorizationId = "A-${index + 1}"
            assertIs<Success<Boolean>>(
                service.recordSaleAuthorized(
                    SaleAuthorizedIntegrationEvent(
                        9001,
                        plan.id.value,
                        listOf(
                            ContractAuthorizedSaleItem(
                                authorizationId,
                                plan.items.single().offerId,
                                plan.items.single().skuId,
                                1,
                                plan.items.single().fulfillmentNodeId,
                                expiresAt,
                            )
                        ),
                        "sale-event-${index + 1}",
                        Instant.parse("2029-01-01T00:00:00Z"),
                    )
                )
            )
            assertIs<Success<Boolean>>(
                service.recordInventoryReserved(
                    InventoryReservedIntegrationEvent(
                        9001,
                        plan.id.value,
                        listOf(authorizationId),
                        listOf("R-${index + 1}"),
                        expiresAt.minusSeconds(60),
                        "inventory-event-${index + 1}",
                        Instant.parse("2029-01-01T00:01:00Z"),
                    )
                )
            )
        }

        service.recordOrderCreated(
            OrderCreatedFromTradeIntegrationEvent(
                9001,
                9101,
                7001,
                "order-created",
                Instant.parse("2029-01-01T00:01:30Z"),
            )
        )
        service.recordOrderCreationRejected(
            OrderCreationRejectedFromTradeIntegrationEvent(
                9001,
                9102,
                "order rejected",
                "order-rejected",
                Instant.parse("2029-01-01T00:01:31Z"),
            )
        )

        assertEquals(TradeStatus.FAILED, repository.trade.status)
        assertEquals(listOf(9101L), orders.cancellationRequests)
        assertEquals(7001L, repository.trade.plan(TradeOrderPlanId(9101)).orderId)
        assertEquals(2, messages.messages.filterIsInstance<ReleaseInventoryCommand>().size)
        assertEquals(2, messages.messages.filterIsInstance<ReleaseSaleAuthorizationCommand>().size)
    }

    @Test
    fun `late order success after another plan failed is recorded and cancelled`() {
        val repository = SagaTradeRepository(trade(planCount = 2))
        val messages = CapturingSagaPublisher()
        val orders = CapturingOrderGateway()
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                orders,
                { _, _ -> Success(Unit) },
                messages,
            )
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
        repository.trade.orderPlans.forEachIndexed { index, plan ->
            val authorizationId = "A-${index + 1}"
            service.recordSaleAuthorized(
                SaleAuthorizedIntegrationEvent(
                    9001,
                    plan.id.value,
                    listOf(
                        ContractAuthorizedSaleItem(
                            authorizationId,
                            plan.items.single().offerId,
                            plan.items.single().skuId,
                            1,
                            plan.items.single().fulfillmentNodeId,
                            expiresAt,
                        )
                    ),
                    "sale-event-${index + 1}",
                    Instant.parse("2029-01-01T00:00:00Z"),
                )
            )
            service.recordInventoryReserved(
                InventoryReservedIntegrationEvent(
                    9001,
                    plan.id.value,
                    listOf(authorizationId),
                    listOf("R-${index + 1}"),
                    expiresAt.minusSeconds(60),
                    "inventory-event-${index + 1}",
                    Instant.parse("2029-01-01T00:01:00Z"),
                )
            )
        }
        service.recordOrderCreationRejected(
            OrderCreationRejectedFromTradeIntegrationEvent(
                9001,
                9101,
                "first order rejected",
                "order-rejected",
                Instant.parse("2029-01-01T00:01:30Z"),
            )
        )

        val result =
            service.recordOrderCreated(
                OrderCreatedFromTradeIntegrationEvent(
                    9001,
                    9102,
                    7002,
                    "late-order-created",
                    Instant.parse("2029-01-01T00:01:31Z"),
                )
            )

        assertEquals(true, assertIs<Success<Boolean>>(result).value)
        assertEquals(TradeStatus.FAILED, repository.trade.status)
        assertEquals(7002L, repository.trade.plan(TradeOrderPlanId(9102)).orderId)
        assertEquals(listOf(9102L), orders.cancellationRequests)
    }

    private fun trade(planCount: Int = 1) =
        Trade.start(
            TradeId(9001),
            "checkout-1",
            "v1:digest",
            BuyerPartySnapshot(PartyType.INDIVIDUAL, 42),
            TradeBuyerProfileSnapshot("张三", "+8613800138000"),
            AuthenticatedAccountSnapshot("issuer-a", 42),
            TradeRecipientSnapshot(
                "张三",
                "CN",
                "+8613800138000",
                null,
                "110105",
                "示例路 1 号",
                address(),
            ),
            (1..planCount).map { index ->
                TradeOrderPlan(
                    TradeOrderPlanId(9100L + index),
                    6L + index,
                    "NODE-$index",
                    listOf(
                        TradeItemSnapshot(
                            10L + index,
                            70L + index,
                            200L + index,
                            100L + index,
                            1,
                            1,
                            1,
                            "NODE-$index",
                            "WEB",
                            Price.ofFen(1000),
                            "商品$index",
                            "规格$index",
                        )
                    ),
                    Price.ofFen(1000),
                )
            },
            "CNY",
            CommitmentPolicySnapshot(TradeMode.NORMAL),
            SettlementTermsSnapshot(
                SettlementMode.PREPAID,
                FulfillmentReleaseRule.FULL_PAYMENT,
                listOf(
                    PaymentInstallmentSnapshot(
                        "FULL",
                        InstallmentPurpose.FULL,
                        Price.ofFen(1000L * planCount),
                    )
                ),
            ),
        )

    private fun address() =
        I18nGeoAddress(
            CountryCode.CN,
            listOf(
                AddressComponent(
                    "110105",
                    DivisionLevel(3, "district"),
                    mapOf(Locale.CHINA to "朝阳区"),
                    Locale.CHINA,
                )
            ),
        )
}

private class SagaTradeRepository(var trade: Trade) : TradeRepository {
    override fun save(aggregate: Trade): Trade = aggregate.also { trade = it }

    override fun findById(id: TradeId): Trade? = trade.takeIf { it.id == id }

    override fun findByCheckoutRequest(
        actingPrincipal: AuthenticatedAccountSnapshot,
        checkoutRequestId: String,
    ): Trade? = trade.takeIf {
        it.actingPrincipal == actingPrincipal && it.checkoutRequestId == checkoutRequestId
    }

    override fun findByOrderPlanId(orderPlanId: TradeOrderPlanId): Trade? = trade.takeIf {
        it.orderPlans.any { plan -> plan.id == orderPlanId }
    }
}

private class CapturingSagaPublisher : IntegrationMessagePublisher {
    val messages = mutableListOf<IntegrationMessage>()

    override fun publish(message: IntegrationMessage) {
        messages += message
    }
}

private class CapturingOrderGateway : TradeOrderCreationGateway {
    val creationRequests = mutableListOf<Long>()
    val cancellationRequests = mutableListOf<Long>()

    override fun requestOrderCreation(
        trade: Trade,
        plan: TradeOrderPlan,
        sourceMessageId: String,
        occurredAt: Instant,
    ) = Success(Unit).also { creationRequests += plan.id.value }

    override fun requestOrderCancellation(
        trade: Trade,
        plan: TradeOrderPlan,
        reason: String,
        sourceMessageId: String,
        occurredAt: Instant,
    ) = Success(Unit).also { cancellationRequests += plan.id.value }
}
