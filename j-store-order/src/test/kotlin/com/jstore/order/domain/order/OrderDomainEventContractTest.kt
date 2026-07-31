package com.jstore.order.domain.order

import com.jstore.common.properties.Price
import com.jstore.order.domain.order.event.OrderPaidEvent
import com.jstore.order.domain.order.event.OrderRefundRequestedEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OrderDomainEventContractTest : FunSpec({
    test("payment and refund events retain stable names versions and payloads") {
        val order = testOrder()
        order.confirmStock()
        order.pay(Price.ofFen(100))
        val paid = order.domainEventQueue.last() as OrderPaidEvent
        paid.eventName shouldBe "order.paid"
        paid.eventVersion shouldBe 1
        paid.paidAmount shouldBe Price.ofFen(100)
        paid.items.single().quantity shouldBe 1

        order.requestRefund(RefundReason(RefundCategory.OTHER, "退款"), listOf(OrderItemId(1)))
        val refund = order.domainEventQueue.last() as OrderRefundRequestedEvent
        refund.eventName shouldBe "order.refund-requested"
        refund.eventVersion shouldBe 1
        refund.refundAmount shouldBe Price.ofFen(100)
        refund.refundItemIds shouldBe listOf(OrderItemId(1))
        refund.requireReturn shouldBe false
    }
})
