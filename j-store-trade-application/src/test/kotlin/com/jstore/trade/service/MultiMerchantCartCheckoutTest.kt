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
import com.jstore.common.utils.Success
import com.jstore.trade.domain.*
import java.time.Instant
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MultiMerchantCartCheckoutTest {
    @Test
    fun `two merchants three items create one trade and two amount-conserving plans`() {
        val repository = MultiMerchantTradeRepository()
        val command =
            CreateCheckoutCommand(
                "cart-checkout",
                "issuer-a",
                42,
                CheckoutRecipient("张三", "CN", "13800000000", null, "110105", "示例路"),
                cartId = 9,
                expectedCartVersion = 4,
            )
        val items =
            listOf(
                CheckoutItem(11, 1, 101, 1001, 1, 1),
                CheckoutItem(12, 1, 102, 1002, 2, 1),
                CheckoutItem(21, 1, 201, 2001, 1, 1),
            )
        val ids = ArrayDeque(listOf(9001L, 9002L, 8001L))
        val service =
            CheckoutApplicationService(
                preparation = { resolved -> Success(prepared(resolved)) },
                trades = repository,
                ids = { ids.removeFirst() },
                authorization = { _, _ -> },
                source = { Success(command.copy(items = items, cartDigest = "cart-digest")) },
            )

        val accepted = assertIs<Success<CheckoutAccepted>>(service.checkout(command)).value
        val trade = requireNotNull(repository.findById(TradeId(accepted.tradeId)))

        assertEquals(2, trade.orderPlans.size)
        assertEquals(setOf(7L, 8L), trade.orderPlans.map { it.merchantId }.toSet())
        assertEquals(Price.ofFen(3500), trade.payableAmount)
        assertEquals(trade.payableAmount, Price.sumOf(trade.orderPlans.map { it.payableAmount }))
        assertEquals(CheckoutSourceType.CART, trade.sourceSnapshot.type)

        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
        trade.orderPlans.forEachIndexed { index, plan ->
            trade.recordSaleAuthorized(
                plan.id,
                plan.items.map {
                    TradeAuthorization("A-$index-${it.offerId}", it.offerId, expiresAt)
                },
            )
            trade.recordInventoryReserved(plan.id, listOf("R-$index"), expiresAt)
        }
        trade.startOrderCreation()
        trade.orderPlans.forEachIndexed { index, plan ->
            trade.recordOrderCreated(plan.id, 7001L + index)
        }
        assertEquals(listOf(7001L, 7002L), trade.orderPlans.mapNotNull { it.orderId })
    }

    private fun prepared(command: CreateCheckoutCommand): PreparedCheckout =
        PreparedCheckout(
            command,
            command.items.mapIndexed { index, item ->
                val merchant = if (index < 2) 7L else 8L
                val node = if (index < 2) "NODE-A" else "NODE-B"
                PreparedCheckoutItem(
                    item.offerId,
                    merchant,
                    merchant,
                    item.spuId,
                    item.skuId,
                    item.quantity,
                    item.catalogSnapshotVersion,
                    item.offerVersion,
                    node,
                    "ONLINE",
                    Price.ofFen(if (index == 0) 500 else 1000),
                    "商品",
                    "规格",
                )
            },
            TradeBuyerProfileSnapshot("张三", "13800000000"),
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
            ),
            "CNY",
        )
}

private class MultiMerchantTradeRepository : TradeRepository {
    private val values = mutableMapOf<TradeId, Trade>()

    override fun save(aggregate: Trade) = aggregate.also { values[it.id] = it }

    override fun findById(id: TradeId) = values[id]

    override fun findByCheckoutRequest(
        actingPrincipal: AuthenticatedAccountSnapshot,
        checkoutRequestId: String,
    ) =
        values.values.firstOrNull {
            it.actingPrincipal == actingPrincipal && it.checkoutRequestId == checkoutRequestId
        }

    override fun findByOrderPlanId(orderPlanId: TradeOrderPlanId) =
        values.values.firstOrNull { trade -> trade.orderPlans.any { it.id == orderPlanId } }
}
