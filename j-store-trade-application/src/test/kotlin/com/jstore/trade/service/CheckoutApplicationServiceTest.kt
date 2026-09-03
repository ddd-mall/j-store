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
import com.jstore.trade.domain.*
import java.time.Instant
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CheckoutApplicationServiceTest {
    @Test
    fun `same numeric account id from another authentication domain cannot read checkout`() {
        val repository = FakeTradeRepository()
        val ids = ArrayDeque(listOf(9101L, 9102L, 9001L))
        val service =
            CheckoutApplicationService(
                { Success(prepared(it)) },
                repository,
                { ids.removeFirst() },
                { _, _ -> },
            )
        service.checkout(checkoutCommand())

        val result = service.find("issuer-b", 42, 9001)

        assertEquals(Failure(TradeErrors.NOT_FOUND), result)
    }

    @Test
    fun `checkout does not query or expose payment before payment is ready`() {
        val repository = FakeTradeRepository()
        val ids = ArrayDeque(listOf(9101L, 9102L, 9001L))
        val service =
            CheckoutApplicationService(
                { Success(prepared(it)) },
                repository,
                { ids.removeFirst() },
                { _, _ -> },
                { error("payment must not be queried before PAYMENT_READY") },
            )
        service.checkout(checkoutCommand())

        val view = assertIs<Success<CheckoutView>>(service.find("issuer-a", 42, 9001)).value

        assertNull(view.payment)
    }

    @Test
    fun `checkout exposes payment action only after trade records ready payment`() {
        val repository = FakeTradeRepository()
        val ids = ArrayDeque(listOf(9101L, 9102L, 9001L))
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
        val service =
            CheckoutApplicationService(
                { Success(prepared(it)) },
                repository,
                { ids.removeFirst() },
                { _, _ -> },
                {
                    CheckoutPaymentView(
                        it,
                        "READY",
                        3000,
                        "CNY",
                        "opaque-payment-action",
                        expiresAt,
                    )
                },
            )
        service.checkout(checkoutCommand())
        val trade = requireNotNull(repository.findById(TradeId(9001)))
        trade.orderPlans.forEachIndexed { index, plan ->
            trade.recordSaleAuthorized(
                plan.id,
                listOf(TradeAuthorization("A-$index", plan.items.single().offerId, expiresAt)),
            )
            trade.recordInventoryReserved(plan.id, listOf("R-$index"), expiresAt)
        }
        trade.startOrderCreation()
        trade.orderPlans.forEachIndexed { index, plan ->
            trade.recordOrderCreated(plan.id, 7001L + index)
        }
        trade.prepareSettlement(SettlementPlanId(9901))
        trade.recordPaymentPrepared(
            SettlementPlanId(9901),
            "FULL",
            8001,
            Price.ofFen(3000),
            "CNY",
        )

        val view = assertIs<Success<CheckoutView>>(service.find("issuer-a", 42, 9001)).value

        assertEquals(8001, view.payment?.paymentId)
        assertEquals("opaque-payment-action", view.payment?.payAction)
    }

    @Test
    fun `expired payment action does not make checkout status unavailable`() {
        val repository = FakeTradeRepository()
        val ids = ArrayDeque(listOf(9101L, 9102L, 9001L))
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
        val service =
            CheckoutApplicationService(
                { Success(prepared(it)) },
                repository,
                { ids.removeFirst() },
                { _, _ -> },
                { null },
            )
        service.checkout(checkoutCommand())
        val trade = requireNotNull(repository.findById(TradeId(9001)))
        trade.orderPlans.forEachIndexed { index, plan ->
            trade.recordSaleAuthorized(
                plan.id,
                listOf(TradeAuthorization("A-$index", plan.items.single().offerId, expiresAt)),
            )
            trade.recordInventoryReserved(plan.id, listOf("R-$index"), expiresAt)
        }
        trade.startOrderCreation()
        trade.orderPlans.forEachIndexed { index, plan ->
            trade.recordOrderCreated(plan.id, 7001L + index)
        }
        trade.prepareSettlement(SettlementPlanId(9901))
        trade.recordPaymentPrepared(
            SettlementPlanId(9901),
            "FULL",
            8001,
            Price.ofFen(3000),
            "CNY",
        )

        val view = assertIs<Success<CheckoutView>>(service.find("issuer-a", 42, 9001)).value

        assertEquals(TradeStatus.PAYMENT_READY.name, view.status)
        assertNull(view.payment)
    }

    @Test
    fun `checkout creates an independent trade and plans before orders exist`() {
        val repository = FakeTradeRepository()
        val requested = mutableListOf<Long>()
        val ids = ArrayDeque(listOf(9101L, 9102L, 9001L))
        val service =
            CheckoutApplicationService(
                { Success(prepared(it)) },
                repository,
                { ids.removeFirst() },
                { _, plan -> requested += plan.id.value },
            )

        val accepted =
            assertIs<Success<CheckoutAccepted>>(service.checkout(checkoutCommand())).value

        assertEquals(9001, accepted.tradeId)
        assertEquals(emptyList(), accepted.orderIds)
        assertEquals(listOf(9101L, 9102L), requested)
        val trade = repository.findById(TradeId(9001))!!
        assertEquals(PartyType.INDIVIDUAL, trade.buyerParty.partyType)
        assertEquals(AuthenticatedAccountSnapshot("issuer-a", 42), trade.actingPrincipal)
        assertEquals(setOf(7L, 8L), trade.orderPlans.map { it.merchantId }.toSet())
        assertEquals(SettlementMode.PREPAID, trade.settlementTerms.mode)
    }

    @Test
    fun `same buyer request is idempotent and conflicting content is rejected`() {
        val repository = FakeTradeRepository()
        val ids = ArrayDeque(listOf(9101L, 9102L, 9001L))
        var authorizations = 0
        val service =
            CheckoutApplicationService(
                { Success(prepared(it)) },
                repository,
                { ids.removeFirst() },
                { _, _ -> authorizations++ },
            )
        val command = checkoutCommand()

        val first = assertIs<Success<CheckoutAccepted>>(service.checkout(command)).value
        val duplicate = assertIs<Success<CheckoutAccepted>>(service.checkout(command)).value
        val conflict =
            service.checkout(command.copy(items = command.items.map { it.copy(quantity = 2) }))

        assertEquals(first, duplicate)
        assertEquals(2, authorizations)
        assertEquals(
            "Trade.StartConflict",
            assertIs<Failure<*>>(conflict)
                .error
                .let { it as com.jstore.common.errors.BusinessError }
                .errorCode,
        )
    }

    @Test
    fun `delimiter characters cannot make different checkout requests share a digest`() {
        val repository = FakeTradeRepository()
        val ids = ArrayDeque(listOf(9101L, 9102L, 9001L))
        val service =
            CheckoutApplicationService(
                { Success(prepared(it)) },
                repository,
                { ids.removeFirst() },
                { _, _ -> },
            )
        val first =
            checkoutCommand()
                .copy(
                    recipient =
                        checkoutCommand().recipient.copy(name = "Alice|CN", countryCode = "US")
                )
        val collidingUnderDelimiterConcatenation =
            first.copy(recipient = first.recipient.copy(name = "Alice", countryCode = "CN|US"))

        assertIs<Success<CheckoutAccepted>>(service.checkout(first))
        val conflict = service.checkout(collidingUnderDelimiterConcatenation)

        assertEquals(
            "Trade.StartConflict",
            assertIs<Failure<*>>(conflict)
                .error
                .let { it as com.jstore.common.errors.BusinessError }
                .errorCode,
        )
    }

    @Test
    fun `checkout request id is mandatory`() {
        val result =
            CheckoutApplicationService(
                    { Success(prepared(it)) },
                    FakeTradeRepository(),
                    { 1 },
                    { _, _ -> error("unused") },
                )
                .checkout(checkoutCommand().copy(checkoutRequestId = " "))

        assertEquals(
            "Trade.CheckoutRequestInvalid",
            assertIs<Failure<*>>(result)
                .error
                .let { it as com.jstore.common.errors.BusinessError }
                .errorCode,
        )
    }

    @Test
    fun `oversized public checkout fields are rejected before preparation`() {
        var preparationCalled = false
        val service =
            CheckoutApplicationService(
                {
                    preparationCalled = true
                    Success(prepared(it))
                },
                FakeTradeRepository(),
                { 1 },
                { _, _ -> },
            )
        val command = checkoutCommand()
        val invalidCommands =
            listOf(
                command.copy(checkoutRequestId = "x".repeat(129)),
                command.copy(recipient = command.recipient.copy(name = "x".repeat(257))),
                command.copy(recipient = command.recipient.copy(detailAddress = "x".repeat(1025))),
                command.copy(
                    recipient =
                        command.recipient.copy(customsFields = mapOf("x".repeat(129) to "v"))
                ),
            )

        invalidCommands.forEach { invalid ->
            val result = service.checkout(invalid)
            assertEquals(
                "Trade.CheckoutRequestInvalid",
                assertIs<Failure<*>>(result)
                    .error
                    .let { it as com.jstore.common.errors.BusinessError }
                    .errorCode,
            )
        }
        assertEquals(false, preparationCalled)
    }

    @Test
    fun `same id with changed recipient contact or customs data conflicts`() {
        val repository = FakeTradeRepository()
        val ids = ArrayDeque(listOf(9101L, 9102L, 9001L))
        val service =
            CheckoutApplicationService(
                { Success(prepared(it)) },
                repository,
                { ids.removeFirst() },
                { _, _ -> },
            )
        val command = checkoutCommand()
        assertIs<Success<CheckoutAccepted>>(service.checkout(command))

        val changed =
            command.copy(
                recipient =
                    command.recipient.copy(
                        phone = "+8613900139000",
                        customsFields = mapOf("taxId" to "CN-42"),
                    )
            )

        assertEquals(
            "Trade.StartConflict",
            assertIs<Failure<*>>(service.checkout(changed))
                .error
                .let { it as com.jstore.common.errors.BusinessError }
                .errorCode,
        )
    }

    @Test
    fun `invalid recipient and duplicate offer are rejected before preparation`() {
        var preparationCalled = false
        val service =
            CheckoutApplicationService(
                {
                    preparationCalled = true
                    Success(prepared(it))
                },
                FakeTradeRepository(),
                { 1 },
                { _, _ -> },
            )
        val command = checkoutCommand()

        assertIs<Failure<*>>(
            service.checkout(
                command.copy(
                    recipient = command.recipient.copy(phone = null, email = null),
                    items = listOf(command.items.first(), command.items.first()),
                )
            )
        )
        assertEquals(false, preparationCalled)
    }

    private fun prepared(command: CreateCheckoutCommand) =
        PreparedCheckout(
            command,
            command.items.mapIndexed { index, item ->
                PreparedCheckoutItem(
                    item.offerId,
                    70 + index.toLong(),
                    7 + index.toLong(),
                    item.spuId,
                    item.skuId,
                    item.quantity,
                    item.catalogSnapshotVersion,
                    item.offerVersion,
                    "NODE-${index + 1}",
                    "WEB",
                    Price.ofFen(1000L * (index + 1)),
                    "商品-${item.spuId}",
                    "规格-${item.skuId}",
                )
            },
            TradeBuyerProfileSnapshot("张三", "+8613800138000"),
            address(),
            "CNY",
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

    private fun checkoutCommand() =
        CreateCheckoutCommand(
            "checkout-20260815-1",
            "issuer-a",
            42,
            CheckoutRecipient("张三", "CN", "+8613800138000", null, "110105", "示例路 1 号"),
            listOf(
                CheckoutItem(11, 2, 21, 22, 1, 3),
                CheckoutItem(12, 2, 23, 24, 1, 3),
            ),
        )
}

private class FakeTradeRepository : TradeRepository {
    private val values = mutableMapOf<TradeId, Trade>()

    override fun save(aggregate: Trade): Trade = aggregate.also { values[it.id] = it }

    override fun findById(id: TradeId): Trade? = values[id]

    override fun findByCheckoutRequest(
        actingPrincipal: AuthenticatedAccountSnapshot,
        checkoutRequestId: String,
    ): Trade? =
        values.values.firstOrNull {
            it.actingPrincipal == actingPrincipal && it.checkoutRequestId == checkoutRequestId
        }

    override fun findByOrderPlanId(orderPlanId: TradeOrderPlanId): Trade? =
        values.values.firstOrNull { trade -> trade.orderPlans.any { it.id == orderPlanId } }
}
