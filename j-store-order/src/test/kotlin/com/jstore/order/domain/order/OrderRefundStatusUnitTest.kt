package com.jstore.order.domain.order

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.event.OrderRefundApprovedEvent
import com.jstore.order.domain.order.event.OrderRefundRequestedEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class OrderRefundStatusUnitTest : FunSpec({
    val reason = RefundReason(RefundCategory.OTHER, "测试退款")

    test("partial approval and subsequent request preserve forward facts") {
        val order = testOrder(
            trade = TradeStatus.ACTIVE,
            payment = PaymentStatus.PAID,
            fulfillment = FulfillmentStatus.DELIVERED,
            itemStatuses = listOf(OrderItemStatus.SHIPPING_FINISHED, OrderItemStatus.SHIPPING_FINISHED),
        )
        order.requestRefund(reason, listOf(OrderItemId(1))).shouldBeInstanceOf<Success<Unit>>()
        (order.domainEventQueue.last() as OrderRefundRequestedEvent).requireReturn shouldBe true
        order.approveRefund(listOf(OrderItemId(1))).shouldBeInstanceOf<Success<Unit>>()
        order.paymentStatus shouldBe PaymentStatus.PARTIALLY_REFUNDED
        order.afterSaleStatus shouldBe AfterSaleStatus.PARTIALLY_COMPLETED
        order.fulfillmentStatus shouldBe FulfillmentStatus.DELIVERED

        order.requestRefund(reason, listOf(OrderItemId(2))).shouldBeInstanceOf<Success<Unit>>()
        order.afterSaleStatus shouldBe AfterSaleStatus.PARTIALLY_COMPLETED
        order.approveRefund(listOf(OrderItemId(2))).shouldBeInstanceOf<Success<Unit>>()
        order.tradeStatus shouldBe TradeStatus.CLOSED
        order.paymentStatus shouldBe PaymentStatus.REFUNDED
        order.fulfillmentStatus shouldBe FulfillmentStatus.DELIVERED
        order.afterSaleStatus shouldBe AfterSaleStatus.COMPLETED
        (order.domainEventQueue.last() as OrderRefundApprovedEvent).requireReturn shouldBe true
    }

    test("rejection restores only selected items and derives summary") {
        val order = testOrder(
            trade = TradeStatus.ACTIVE,
            payment = PaymentStatus.PAID,
            itemStatuses = listOf(OrderItemStatus.NONE, OrderItemStatus.NONE),
        )
        order.requestRefund(reason, listOf(OrderItemId(1), OrderItemId(2))).shouldBeInstanceOf<Success<Unit>>()
        order.rejectRefund("拒绝一个", listOf(OrderItemId(1))).shouldBeInstanceOf<Success<Unit>>()
        order.afterSaleStatus shouldBe AfterSaleStatus.PROCESSING
        order.items.first().status shouldBe OrderItemStatus.NONE
        order.items.last().status shouldBe OrderItemStatus.REFUNDING
        order.rejectRefund("全部拒绝", listOf(OrderItemId(2))).shouldBeInstanceOf<Success<Unit>>()
        order.afterSaleStatus shouldBe AfterSaleStatus.NONE
    }

    test("duplicate refund targets fail atomically") {
        val order = testOrder(trade = TradeStatus.ACTIVE, payment = PaymentStatus.PAID)
        val before = order.items.map { it.status } to order.domainEventQueue.toList()
        order.requestRefund(reason, listOf(OrderItemId(1), OrderItemId(1))).shouldBeInstanceOf<Failure<*>>()
        (order.items.map { it.status } to order.domainEventQueue.toList()) shouldBe before
    }
})
