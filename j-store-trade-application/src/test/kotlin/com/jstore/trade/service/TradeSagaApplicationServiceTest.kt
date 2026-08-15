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
        var createdOrders = 0
        var preparedSettlement: Long? = null
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                { _, _ -> Success(7001L).also { createdOrders++ } },
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
        assertEquals(0, createdOrders)
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

        assertEquals(1, createdOrders)
        assertEquals(9901, preparedSettlement)
        assertEquals(TradeStatus.SETTLEMENT_PREPARING, repository.trade.status)
        assertEquals(7001, repository.trade.orderPlans.single().orderId)
    }

    @Test
    fun `inventory failure persists terminal failure and releases sale authorization`() {
        val repository = SagaTradeRepository(trade())
        val messages = CapturingSagaPublisher()
        var createdOrders = 0
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                { _, _ -> Success(7001L).also { createdOrders++ } },
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
        assertEquals(0, createdOrders)
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
                { _, _ -> Success(7001L) },
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
                { _, _ -> Success(7001L) },
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
    fun `buyer cancellation fails trade and closes sibling order and settlement`() {
        val repository = SagaTradeRepository(trade(planCount = 2))
        val messages = CapturingSagaPublisher()
        val cancelledPlans = mutableListOf<Long>()
        var nextOrderId = 7000L
        var cancelledSettlement: Long? = null
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                object : TradeOrderCreationGateway {
                    override fun createOrder(trade: Trade, plan: TradeOrderPlan) =
                        Success(++nextOrderId)

                    override fun cancelOrder(plan: TradeOrderPlan, reason: String) =
                        Success(Unit).also { cancelledPlans += plan.id.value }
                },
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
        assertEquals(TradeStatus.FAILED, repository.trade.status)
        assertEquals(listOf(9102L), cancelledPlans)
        assertEquals(9901L, cancelledSettlement)
        assertEquals(
            setOf(TradeOrderPlanStatus.CLOSED),
            repository.trade.orderPlans.map { it.status }.toSet(),
        )
        assertEquals(2, messages.messages.filterIsInstance<ReleaseInventoryCommand>().size)
        assertEquals(2, messages.messages.filterIsInstance<ReleaseSaleAuthorizationCommand>().size)
    }

    @Test
    fun `order business rejection fails trade and compensates reserved resources`() {
        val repository = SagaTradeRepository(trade())
        val messages = CapturingSagaPublisher()
        var settlementPrepared = false
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                { _, _ -> Failure(BusinessError("order rejected", "Order.Rejected", 409)) },
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

        val result =
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
        val cancelledPlans = mutableListOf<Long>()
        val service =
            TradeSagaApplicationService(
                repository,
                { 9901 },
                object : TradeOrderCreationGateway {
                    override fun createOrder(
                        trade: Trade,
                        plan: TradeOrderPlan,
                    ) =
                        if (plan.id == TradeOrderPlanId(9101)) {
                            Success(7001L)
                        } else {
                            Failure(BusinessError("order rejected", "Order.Rejected", 409))
                        }

                    override fun cancelOrder(
                        plan: TradeOrderPlan,
                        reason: String,
                    ) = Success(Unit).also { cancelledPlans += plan.id.value }
                },
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

        assertEquals(TradeStatus.FAILED, repository.trade.status)
        assertEquals(listOf(9101L), cancelledPlans)
        assertEquals(7001L, repository.trade.plan(TradeOrderPlanId(9101)).orderId)
        assertEquals(2, messages.messages.filterIsInstance<ReleaseInventoryCommand>().size)
        assertEquals(2, messages.messages.filterIsInstance<ReleaseSaleAuthorizationCommand>().size)
    }

    private fun trade(planCount: Int = 1) =
        Trade.start(
            TradeId(9001),
            "checkout-1",
            "v1:digest",
            BuyerPartySnapshot(PartyType.INDIVIDUAL, 42),
            TradeBuyerProfileSnapshot("张三", "+8613800138000"),
            42,
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
        buyerParty: BuyerPartySnapshot,
        checkoutRequestId: String,
    ): Trade? = trade.takeIf {
        it.buyerParty == buyerParty && it.checkoutRequestId == checkoutRequestId
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
