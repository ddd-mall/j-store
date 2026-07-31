package com.jstore.order.domain.order

import com.jstore.common.utils.Success
import com.jstore.order.domain.order.event.OrderRefundApprovedEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class OrderRefundApprovalPropertyTest : FunSpec({
    test("full approval preserves every fulfillment fact and return decision") {
        FulfillmentStatus.entries.forEach { fulfillment ->
            val order = testOrder(
                trade = TradeStatus.ACTIVE,
                payment = PaymentStatus.PAID,
                fulfillment = fulfillment,
                afterSale = AfterSaleStatus.PROCESSING,
                itemStatuses = listOf(OrderItemStatus.REFUNDING),
            )
            order.approveRefund(listOf(OrderItemId(1))).shouldBeInstanceOf<Success<Unit>>()
            order.fulfillmentStatus shouldBe fulfillment
            order.tradeStatus shouldBe TradeStatus.CLOSED
            order.paymentStatus shouldBe PaymentStatus.REFUNDED
            order.afterSaleStatus shouldBe AfterSaleStatus.COMPLETED
            (order.domainEventQueue.last() as OrderRefundApprovedEvent).requireReturn shouldBe
                (fulfillment == FulfillmentStatus.SHIPPED || fulfillment == FulfillmentStatus.DELIVERED)
        }
    }
})
