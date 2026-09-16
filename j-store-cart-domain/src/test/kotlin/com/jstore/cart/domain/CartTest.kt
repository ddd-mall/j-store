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

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CartTest {
    @Test
    fun `setting an item quantity converges when a stale retry already reached its target`() {
        val cart = Cart.create(CartId(1), BuyerId(7), scope())

        val first =
            cart.setItemQuantity(
                expectedVersion = 0,
                lineId = CartLineId(11),
                skuId = SkuId(101),
                offerId = OfferId(201),
                merchantId = MerchantId(301),
                targetQuantity = 3,
                scope = scope(),
                limits = CartLimits(1, 999, 100),
            )
        val retry =
            cart.setItemQuantity(
                expectedVersion = 0,
                lineId = CartLineId(12),
                skuId = SkuId(101),
                offerId = OfferId(201),
                merchantId = MerchantId(301),
                targetQuantity = 3,
                scope = scope(),
                limits = CartLimits(1, 999, 100),
            )

        assertEquals(true, assertIs<Success<Boolean>>(first).value)
        assertEquals(false, assertIs<Success<Boolean>>(retry).value)
        assertEquals(1, cart.contentVersion)
        assertEquals(3, cart.lines.single().quantity)
    }

    @Test
    fun `setting a different target from a stale version is rejected`() {
        val cart = Cart.create(CartId(1), BuyerId(7), scope())
        cart.setItemQuantity(
            expectedVersion = 0,
            lineId = CartLineId(11),
            skuId = SkuId(101),
            offerId = OfferId(201),
            merchantId = MerchantId(301),
            targetQuantity = 2,
            scope = scope(),
            limits = CartLimits(1, 999, 100),
        )

        val result =
            cart.setItemQuantity(
                expectedVersion = 0,
                lineId = CartLineId(12),
                skuId = SkuId(101),
                offerId = OfferId(201),
                merchantId = MerchantId(301),
                targetQuantity = 3,
                scope = scope(),
                limits = CartLimits(1, 999, 100),
            )

        assertEquals(CartErrors.VERSION_CONFLICT, assertIs<Failure<*>>(result).error)
        assertEquals(1, cart.contentVersion)
        assertEquals(2, cart.lines.single().quantity)
    }

    @Test
    fun `selection converges on its target but rejects a stale different target`() {
        val cart = Cart.create(CartId(1), BuyerId(7), scope())
        cart.setItemQuantity(
            0,
            CartLineId(11),
            SkuId(101),
            OfferId(201),
            MerchantId(301),
            1,
            scope(),
            limits = CartLimits(1, 999, 100),
        )
        cart.setItemQuantity(
            1,
            CartLineId(12),
            SkuId(102),
            OfferId(202),
            MerchantId(302),
            1,
            scope(),
            limits = CartLimits(1, 999, 100),
        )

        val first = cart.replaceSelection(2, setOf(CartLineId(12)))
        val retry = cart.replaceSelection(2, setOf(CartLineId(12)))
        val conflicting = cart.replaceSelection(2, setOf(CartLineId(11)))

        assertEquals(true, assertIs<Success<Boolean>>(first).value)
        assertEquals(false, assertIs<Success<Boolean>>(retry).value)
        assertEquals(CartErrors.VERSION_CONFLICT, assertIs<Failure<*>>(conflicting).error)
        assertEquals(3, cart.contentVersion)
    }

    @Test
    fun `cart sets the same offer quantity and allows multiple merchants in one scope`() {
        val cart = Cart.create(CartId(1), BuyerId(7), SettlementScope("CN", "ONLINE", "CNY"))
        assertIs<Success<*>>(
            cart.setItemQuantity(
                0,
                CartLineId(11),
                SkuId(101),
                OfferId(201),
                MerchantId(301),
                2,
                scope(),
                limits = CartLimits(1, 999, 100),
            )
        )
        assertIs<Success<*>>(
            cart.setItemQuantity(
                1,
                CartLineId(12),
                SkuId(101),
                OfferId(201),
                MerchantId(301),
                3,
                scope(),
                limits = CartLimits(1, 999, 100),
            )
        )
        assertIs<Success<*>>(
            cart.setItemQuantity(
                2,
                CartLineId(13),
                SkuId(102),
                OfferId(202),
                MerchantId(302),
                1,
                scope(),
                limits = CartLimits(1, 999, 100),
            )
        )
        assertEquals(2, cart.lines.size)
        assertEquals(3, cart.lines.first { it.offerId.value == 201L }.quantity)
        assertEquals(setOf(301L, 302L), cart.lines.map { it.merchantId.value }.toSet())
        assertEquals(3, cart.contentVersion)
    }

    @Test
    fun `different settlement scope is rejected without changing cart`() {
        val cart = Cart.create(CartId(1), BuyerId(7), scope())
        assertIs<Success<*>>(
            cart.setItemQuantity(
                0,
                CartLineId(11),
                SkuId(101),
                OfferId(201),
                MerchantId(301),
                1,
                scope(),
                limits = CartLimits(1, 999, 100),
            )
        )
        val result =
            cart.setItemQuantity(
                1,
                CartLineId(12),
                SkuId(102),
                OfferId(202),
                MerchantId(302),
                1,
                SettlementScope("US", "ONLINE", "USD"),
                limits = CartLimits(1, 999, 100),
            )
        assertIs<Failure<*>>(result)
        assertEquals(1, cart.lines.size)
        assertEquals(1, cart.contentVersion)
    }

    @Test
    fun `selection is atomically replaced and no-op does not advance version`() {
        val cart = Cart.create(CartId(1), BuyerId(7), scope())
        cart.setItemQuantity(
            0,
            CartLineId(11),
            SkuId(101),
            OfferId(201),
            MerchantId(301),
            1,
            scope(),
            limits = CartLimits(1, 999, 100),
        )
        cart.setItemQuantity(
            1,
            CartLineId(12),
            SkuId(102),
            OfferId(202),
            MerchantId(302),
            1,
            scope(),
            limits = CartLimits(1, 999, 100),
        )
        assertIs<Success<*>>(cart.replaceSelection(2, setOf(CartLineId(12))))
        assertEquals(3, cart.contentVersion)
        assertEquals(setOf(12L), cart.lines.filter { it.selected }.map { it.id.value }.toSet())
        assertIs<Success<*>>(cart.replaceSelection(2, setOf(CartLineId(12))))
        assertEquals(3, cart.contentVersion)
    }

    @Test
    fun `quantity boundaries preserve intent and reject invalid targets`() {
        val cart = Cart.create(CartId(1), BuyerId(7), scope())
        for (quantity in listOf(1, 999)) {
            assertIs<Success<*>>(setQuantity(cart, 11, quantity))
            assertEquals(
                true,
                cart.hasItemTarget(SkuId(11), OfferId(11), quantity, CartLimits(1, 999, 100)),
            )
        }
        val version = cart.contentVersion
        for (quantity in listOf(Int.MIN_VALUE, -1, 0, 1000, Int.MAX_VALUE)) {
            assertEquals(
                CartErrors.INVALID_QUANTITY,
                assertIs<Failure<*>>(setQuantity(cart, 11, quantity)).error,
            )
            assertEquals(
                false,
                cart.hasItemTarget(SkuId(11), OfferId(11), quantity, CartLimits(1, 999, 100)),
            )
        }
        assertEquals(version, cart.contentVersion)
        assertEquals(999, cart.lines.single().quantity)
    }

    @Test
    fun `full cart allows updating existing offers but rejects an additional line`() {
        val cart = Cart.create(CartId(1), BuyerId(7), scope())
        for (id in 1L..100L) assertIs<Success<*>>(setQuantity(cart, id, 1))
        assertEquals(
            CartErrors.LINE_LIMIT,
            assertIs<Failure<*>>(setQuantity(cart, 101, 1)).error,
        )
        assertEquals(100, cart.lines.size)
        assertEquals(100L, cart.contentVersion)
        assertIs<Success<*>>(setQuantity(cart, 1, 999))
        assertEquals(100, cart.lines.size)
        assertEquals(999, cart.lines.first().quantity)
    }

    @Test
    fun `injected limits govern both target convergence and quantity mutations`() {
        val cart = Cart.create(CartId(1), BuyerId(7), scope())
        val limits = CartLimits(minQuantity = 2, maxQuantity = 1500, maxLines = 1)
        fun set(id: Long, quantity: Int) =
            cart.setItemQuantity(
                cart.contentVersion,
                CartLineId(id),
                SkuId(id),
                OfferId(id),
                MerchantId(301),
                quantity,
                scope(),
                limits = limits,
            )
        assertEquals(CartErrors.INVALID_QUANTITY, assertIs<Failure<*>>(set(1, 1)).error)
        assertIs<Success<*>>(set(1, 1500))
        assertEquals(true, cart.hasItemTarget(SkuId(1), OfferId(1), 1500, limits))
        assertEquals(
            false,
            cart.hasItemTarget(
                SkuId(1),
                OfferId(1),
                1500,
                limits.copy(maxQuantity = 1000),
            ),
        )
        assertEquals(CartErrors.LINE_LIMIT, assertIs<Failure<*>>(set(2, 2)).error)
        assertIs<Success<*>>(set(1, 2))
        assertEquals(CartErrors.INVALID_QUANTITY, assertIs<Failure<*>>(set(1, 1501)).error)
        assertEquals(2, cart.lines.single().quantity)
    }

    @Test
    fun `invalid limits are rejected`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { CartLimits(0, 999, 100) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { CartLimits(1, 0, 100) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { CartLimits(3, 2, 100) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { CartLimits(1, 999, 0) }
    }

    private fun setQuantity(cart: Cart, id: Long, quantity: Int) =
        cart.setItemQuantity(
            cart.contentVersion,
            CartLineId(id),
            SkuId(id),
            OfferId(id),
            MerchantId(301),
            quantity,
            scope(),
            limits = CartLimits(1, 999, 100),
        )

    private fun scope() = SettlementScope("CN", "ONLINE", "CNY")
}
