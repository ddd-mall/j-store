package com.jstore.order.domain.order

import com.jstore.common.utils.Failure
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class OrderRefundValidationPropertyTest : FunSpec({
    val reason = RefundReason(RefundCategory.OTHER, "退款")
    test("empty external duplicate and illegal targets never partially modify the aggregate") {
        listOf(
            emptyList(),
            listOf(OrderItemId(99)),
            listOf(OrderItemId(1), OrderItemId(1)),
        ).forEach { ids ->
            val order = testOrder(trade = TradeStatus.ACTIVE, payment = PaymentStatus.PAID)
            val before = atomicSnapshot(order)
            order.requestRefund(reason, ids).shouldBeInstanceOf<Failure<*>>()
            atomicSnapshot(order) shouldBe before
        }
        val canceled = testOrder(
            trade = TradeStatus.ACTIVE,
            payment = PaymentStatus.PARTIALLY_REFUNDED,
            afterSale = AfterSaleStatus.PARTIALLY_COMPLETED,
            itemStatuses = listOf(OrderItemStatus.CANCELED, OrderItemStatus.NONE),
        )
        val before = atomicSnapshot(canceled)
        canceled.requestRefund(reason, listOf(OrderItemId(1))).shouldBeInstanceOf<Failure<*>>()
        atomicSnapshot(canceled) shouldBe before
    }
})

private fun atomicSnapshot(order: Order) = listOf(
    order.tradeStatus, order.paymentStatus, order.fulfillmentStatus, order.afterSaleStatus,
    order.actualPay, order.updateTime, order.items.map { it.status to it.previousItemStatus },
    order.domainEventQueue.toList(),
)
