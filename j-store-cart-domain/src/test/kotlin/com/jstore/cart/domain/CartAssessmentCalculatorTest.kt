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
