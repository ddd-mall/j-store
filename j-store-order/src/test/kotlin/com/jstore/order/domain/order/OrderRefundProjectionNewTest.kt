package com.jstore.order.domain.order

import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.AfterSaleId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class OrderRefundProjectionNewTest : FunSpec({
    test("successful refund is partial, idempotent and leaves fulfillment facts unchanged") {
        val order = testOrder(
            trade = TradeStatus.ACTIVE,
            payment = PaymentStatus.PAID,
            fulfillment = FulfillmentStatus.DELIVERED,
            itemStatuses = listOf(OrderItemStatus.SHIPPING_FINISHED),
        )
        val before = order.fulfillmentStatus
        val first = order.recordRefundSucceeded(
            "refund-9",
            AfterSaleId(9),
            listOf(SuccessfulRefundItem(OrderItemId(1), 1, Price.ofFen(50))),
            Instant.EPOCH,
        ) as Success
        first.value.newlyRegistered shouldBe true
        order.paymentStatus shouldBe PaymentStatus.PARTIALLY_REFUNDED
        order.fulfillmentStatus shouldBe before
        order.items.single().status shouldBe OrderItemStatus.SHIPPING_FINISHED
        val duplicate = order.recordRefundSucceeded(
            "refund-9",
            AfterSaleId(9),
            listOf(SuccessfulRefundItem(OrderItemId(1), 1, Price.ofFen(50))),
            Instant.EPOCH,
        ) as Success
        duplicate.value.newlyRegistered shouldBe false
        order.refundedAmount shouldBe Price.ofFen(50)
    }

    test("invalid refund fact is atomic") {
        val order = testOrder(trade = TradeStatus.ACTIVE, payment = PaymentStatus.PAID)
        (order.recordRefundSucceeded(
            "refund-10",
            AfterSaleId(10),
            listOf(SuccessfulRefundItem(OrderItemId(1), 2, Price.ofFen(200))),
            Instant.EPOCH,
        ) is Failure) shouldBe true
        order.refundedAmount shouldBe Price.ZERO
    }
})
