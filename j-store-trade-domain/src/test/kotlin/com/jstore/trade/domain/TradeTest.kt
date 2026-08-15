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
package com.jstore.trade.domain

import com.jstore.common.geo.AddressComponent
import com.jstore.common.geo.CountryCode
import com.jstore.common.geo.DivisionLevel
import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import java.time.Instant
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TradeTest {
    @Test
    fun `trade identity is independent from every future order identity`() {
        val trade = trade()

        assertEquals(9001, trade.id.value)
        assertEquals(listOf(9101L, 9102L), trade.orderPlans.map { it.id.value })
        assertEquals(listOf(null, null), trade.orderPlans.map { it.orderId })
        trade.orderPlans.forEach { assertNotEquals(trade.id.value, it.orderId) }
    }

    @Test
    fun `buyer party and acting principal are separate facts`() {
        val trade = trade()

        assertEquals(PartyType.ORGANIZATION, trade.buyerParty.partyType)
        assertEquals(88, trade.buyerParty.partyId)
        assertEquals(42, trade.actingPrincipalId)
    }

    @Test
    fun `settlement cannot be prepared until every plan is reserved and has an order`() {
        val trade = trade()

        assertIs<Failure<*>>(trade.prepareSettlement(SettlementPlanId(9901)))
        assertNull(trade.settlementPlanId)

        authorizeAndReserve(trade, TradeOrderPlanId(9101), "A-1", "R-1")
        trade.startOrderCreation().let { assertIs<Failure<*>>(it) }

        authorizeAndReserve(trade, TradeOrderPlanId(9102), "A-2", "R-2")
        assertIs<Success<Boolean>>(trade.startOrderCreation())
        assertIs<Success<Boolean>>(trade.recordOrderCreated(TradeOrderPlanId(9101), 7001))
        assertIs<Failure<*>>(trade.prepareSettlement(SettlementPlanId(9901)))
        assertIs<Success<Boolean>>(trade.recordOrderCreated(TradeOrderPlanId(9102), 7002))
        assertIs<Success<Boolean>>(trade.prepareSettlement(SettlementPlanId(9901)))

        assertEquals(SettlementPlanId(9901), trade.settlementPlanId)
        assertEquals(TradeStatus.SETTLEMENT_PREPARING, trade.status)
    }

    @Test
    fun `duplicate plan result is idempotent but conflicting order is rejected`() {
        val trade = trade()
        authorizeAndReserve(trade, TradeOrderPlanId(9101), "A-1", "R-1")
        authorizeAndReserve(trade, TradeOrderPlanId(9102), "A-2", "R-2")
        assertIs<Success<Boolean>>(trade.startOrderCreation())
        assertEquals(
            true,
            assertIs<Success<Boolean>>(trade.recordOrderCreated(TradeOrderPlanId(9101), 7001))
                .value,
        )
        assertEquals(
            false,
            assertIs<Success<Boolean>>(trade.recordOrderCreated(TradeOrderPlanId(9101), 7001))
                .value,
        )
        assertIs<Failure<*>>(trade.recordOrderCreated(TradeOrderPlanId(9101), 7999))
    }

    private fun authorizeAndReserve(
        trade: Trade,
        planId: TradeOrderPlanId,
        authorizationId: String,
        reservationId: String,
    ) {
        assertIs<Success<Boolean>>(
            trade.recordSaleAuthorized(
                planId,
                listOf(
                    TradeAuthorization(
                        authorizationId,
                        trade.plan(planId).items.single().offerId,
                        Instant.parse("2030-01-01T00:00:00Z"),
                    )
                ),
            )
        )
        assertIs<Success<Boolean>>(
            trade.recordInventoryReserved(
                planId,
                listOf(reservationId),
                Instant.parse("2029-12-31T23:00:00Z"),
            )
        )
    }

    private fun trade() =
        Trade.start(
            id = TradeId(9001),
            checkoutRequestId = "checkout-1",
            requestDigest = "v1:abc",
            buyerParty = BuyerPartySnapshot(PartyType.ORGANIZATION, 88),
            buyerProfile = TradeBuyerProfileSnapshot("示例企业", "+8613800138000"),
            actingPrincipalId = 42,
            recipient =
                TradeRecipientSnapshot(
                    "李四",
                    "CN",
                    "+8613800138000",
                    null,
                    "110105",
                    "示例路 1 号",
                    address(),
                ),
            orderPlans =
                listOf(
                    plan(9101, 7, 11, 101, 1000),
                    plan(9102, 8, 12, 102, 2000),
                ),
            currency = "CNY",
            commitmentPolicy = CommitmentPolicySnapshot(TradeMode.NORMAL),
            settlementTerms =
                SettlementTermsSnapshot(
                    SettlementMode.PREPAID,
                    FulfillmentReleaseRule.FULL_PAYMENT,
                    listOf(
                        PaymentInstallmentSnapshot(
                            "FULL",
                            InstallmentPurpose.FULL,
                            Price.ofFen(3000),
                        )
                    ),
                ),
        )

    private fun plan(id: Long, merchantId: Long, offerId: Long, skuId: Long, amount: Long) =
        TradeOrderPlan(
            TradeOrderPlanId(id),
            merchantId,
            "NODE-1",
            listOf(
                TradeItemSnapshot(
                    offerId,
                    merchantId,
                    200 + skuId,
                    skuId,
                    1,
                    1,
                    1,
                    "NODE-1",
                    "WEB",
                    Price.ofFen(amount),
                    "商品-${200 + skuId}",
                    "规格-$skuId",
                )
            ),
            Price.ofFen(amount),
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
