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
package com.jstore.cart.domain

import com.jstore.common.properties.Price
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CartAssessmentCalculatorTest {
    @Test
    fun `only selected published active and sufficiently stocked lines contribute amount`() {
        val cart = Cart.create(CartId(1), BuyerId(7), SettlementScope("CN", "ONLINE", "CNY"))
        cart.add(CartLineId(1), SkuId(1), OfferId(1), MerchantId(10), 2, cart.settlementScope)
        cart.add(CartLineId(2), SkuId(2), OfferId(2), MerchantId(20), 4, cart.settlementScope)
        cart.add(CartLineId(3), SkuId(3), OfferId(3), MerchantId(20), 1, cart.settlementScope)
        val facts = listOf(
            fact(1, 100, 5),
            fact(2, 200, 0),
            fact(3, 300, 1, offerAvailable = false),
        )
        val assessment = CartAssessmentCalculator.evaluate(CartAssessmentId(9), cart, facts, Instant.EPOCH)
        assertEquals(Price.ofFen(200), assessment.estimatedAmount)
        assertEquals(AssessmentStatus.PARTIAL, assessment.status)
        assertEquals(LineAssessmentStatus.ELIGIBLE, assessment.lines[0].status)
        assertEquals(LineAssessmentStatus.OUT_OF_STOCK, assessment.lines[1].status)
        assertEquals(LineAssessmentStatus.OFFER_UNAVAILABLE, assessment.lines[2].status)
    }

    private fun fact(id: Long, price: Long, atp: Int, offerAvailable: Boolean = true) =
        CartLineCommerceFacts(
            CartLineId(id), true, offerAvailable, Price.ofFen(price), id, id,
            atp, 1, "NODE", "CN", "ONLINE", "CNY", id, "Goods $id", "Sku $id",
        )
}
