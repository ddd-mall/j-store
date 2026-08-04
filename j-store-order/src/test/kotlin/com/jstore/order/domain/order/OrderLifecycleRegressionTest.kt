package com.jstore.order.domain.order

import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OrderLifecycleRegressionTest {
    @Test
    fun `paid order preserves fulfillment sequence through delivery and completion`() {
        val order = testOrder(trade = TradeStatus.ACTIVE, payment = PaymentStatus.PAID)
        assertIs<Success<Boolean>>(order.recordFulfillmentPrepared("fulfillment-1")); assertIs<Success<Boolean>>(order.recordShipmentDispatched("fulfillment-1"))
        assertEquals(FulfillmentStatus.SHIPPED, order.fulfillmentStatus)
        assertEquals(OrderItemStatus.SHIPPING, order.items.single().status)
        assertIs<Success<Boolean>>(order.recordShipmentDelivered("fulfillment-1")); assertIs<Success<Unit>>(order.complete())
        assertEquals(TradeStatus.COMPLETED, order.tradeStatus)
        assertEquals(OrderItemStatus.SHIPPING_FINISHED, order.items.single().status)
    }

    @Test
    fun `unpaid cancellation closes transaction but paid cancellation is atomic failure`() {
        val unpaid = testOrder(trade = TradeStatus.ACTIVE)
        assertIs<Success<Unit>>(unpaid.cancel(CancellationReason(CancellationCategory.BUYER_CANCELLED, "buyer")))
        assertEquals(TradeStatus.CLOSED, unpaid.tradeStatus)
        assertEquals(OrderItemStatus.CANCELED, unpaid.items.single().status)

        val paid = testOrder(trade = TradeStatus.ACTIVE, payment = PaymentStatus.PAID)
        val before = Triple(paid.tradeStatus, paid.paymentStatus, paid.items.single().status)
        assertIs<Failure<*>>(paid.cancel(CancellationReason(CancellationCategory.BUYER_CANCELLED, "late")))
        assertEquals(before, Triple(paid.tradeStatus, paid.paymentStatus, paid.items.single().status))
    }

    @Test
    fun `invalid payment does not partially mutate amount or statuses`() {
        val order = testOrder(trade = TradeStatus.CREATED)
        val before = Triple(order.tradeStatus, order.paymentStatus, order.paidAmount)
        assertIs<Failure<*>>(order.recordPaymentCaptured("payment-1", Price.ofFen(100), "CNY", java.time.Instant.EPOCH))
        assertEquals(before, Triple(order.tradeStatus, order.paymentStatus, order.paidAmount))
    }
}
