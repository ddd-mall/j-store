package com.jstore.order.domain.order

import com.jstore.common.properties.Price

/** 下单时冻结的金额组成。后续商品价格变化不得影响该快照。 */
data class OrderAmountSnapshot(
    val currency: String,
    val itemsSubtotal: Price,
    val discountAmount: Price,
    val shippingAmount: Price,
    val taxAmount: Price,
    val payableAmount: Price,
) {
    init {
        require(currency.matches(Regex("[A-Z]{3}"))) { "currency must be an ISO-4217 code" }
        require(discountAmount <= itemsSubtotal) { "discount cannot exceed item subtotal" }
        require(payableAmount == itemsSubtotal - discountAmount + shippingAmount + taxAmount) {
            "payable amount does not match amount components"
        }
    }

    companion object {
        fun cny(itemsSubtotal: Price): OrderAmountSnapshot =
            OrderAmountSnapshot(
                currency = "CNY",
                itemsSubtotal = itemsSubtotal,
                discountAmount = Price.ZERO,
                shippingAmount = Price.ZERO,
                taxAmount = Price.ZERO,
                payableAmount = itemsSubtotal,
            )
    }
}
