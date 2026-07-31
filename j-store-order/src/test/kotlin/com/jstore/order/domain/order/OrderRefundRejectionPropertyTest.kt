package com.jstore.order.domain.order

import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class OrderRefundRejectionPropertyTest : FunSpec({
    test("rejection after an approved item keeps partial completion and restores only target") {
        val order = testOrder(
            trade = TradeStatus.ACTIVE,
            payment = PaymentStatus.PARTIALLY_REFUNDED,
            fulfillment = FulfillmentStatus.DELIVERED,
            afterSale = AfterSaleStatus.PARTIALLY_COMPLETED,
            itemStatuses = listOf(OrderItemStatus.CANCELED, OrderItemStatus.REFUNDING),
        )
        order.rejectRefund("拒绝", listOf(OrderItemId(2))).shouldBeInstanceOf<Success<Unit>>()
        order.tradeStatus shouldBe TradeStatus.ACTIVE
        order.paymentStatus shouldBe PaymentStatus.PARTIALLY_REFUNDED
        order.fulfillmentStatus shouldBe FulfillmentStatus.DELIVERED
        order.afterSaleStatus shouldBe AfterSaleStatus.PARTIALLY_COMPLETED
        order.items.map { it.status } shouldBe listOf(OrderItemStatus.CANCELED, OrderItemStatus.NONE)
    }
})
