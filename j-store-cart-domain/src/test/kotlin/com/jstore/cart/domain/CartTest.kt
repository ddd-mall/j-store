package com.jstore.cart.domain

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CartTest {
    @Test
    fun `cart merges the same offer and allows multiple merchants in one scope`() {
        val cart = Cart.create(CartId(1), BuyerId(7), SettlementScope("CN", "ONLINE", "CNY"))
        assertIs<Success<*>>(cart.add(CartLineId(11), SkuId(101), OfferId(201), MerchantId(301), 2, scope()))
        assertIs<Success<*>>(cart.add(CartLineId(12), SkuId(101), OfferId(201), MerchantId(301), 3, scope()))
        assertIs<Success<*>>(cart.add(CartLineId(13), SkuId(102), OfferId(202), MerchantId(302), 1, scope()))
        assertEquals(2, cart.lines.size)
        assertEquals(5, cart.lines.first { it.offerId.value == 201L }.quantity)
        assertEquals(setOf(301L, 302L), cart.lines.map { it.merchantId.value }.toSet())
        assertEquals(3, cart.contentVersion)
    }

    @Test
    fun `different settlement scope is rejected without changing cart`() {
        val cart = Cart.create(CartId(1), BuyerId(7), scope())
        assertIs<Success<*>>(cart.add(CartLineId(11), SkuId(101), OfferId(201), MerchantId(301), 1, scope()))
        val result = cart.add(CartLineId(12), SkuId(102), OfferId(202), MerchantId(302), 1, SettlementScope("US", "ONLINE", "USD"))
        assertIs<Failure<*>>(result)
        assertEquals(1, cart.lines.size)
        assertEquals(1, cart.contentVersion)
    }

    @Test
    fun `selection is atomically replaced and no-op does not advance version`() {
        val cart = Cart.create(CartId(1), BuyerId(7), scope())
        cart.add(CartLineId(11), SkuId(101), OfferId(201), MerchantId(301), 1, scope())
        cart.add(CartLineId(12), SkuId(102), OfferId(202), MerchantId(302), 1, scope())
        assertIs<Success<*>>(cart.replaceSelection(setOf(CartLineId(12))))
        assertEquals(3, cart.contentVersion)
        assertEquals(setOf(12L), cart.lines.filter { it.selected }.map { it.id.value }.toSet())
        assertIs<Success<*>>(cart.replaceSelection(setOf(CartLineId(12))))
        assertEquals(3, cart.contentVersion)
    }

    private fun scope() = SettlementScope("CN", "ONLINE", "CNY")
}
