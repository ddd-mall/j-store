package com.jstore.order.domain.order

import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.event.OrderCompletedEvent
import com.jstore.order.domain.order.event.OrderPaidEvent
import com.jstore.order.domain.order.event.OrderShippedEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class OrderStatusDimensionsUnitTest : FunSpec({
    test("four status enums expose only the designed values") {
        TradeStatus.entries.shouldContainExactly(TradeStatus.CREATED, TradeStatus.ACTIVE, TradeStatus.CLOSED, TradeStatus.COMPLETED)
        PaymentStatus.entries.shouldContainExactly(PaymentStatus.UNPAID, PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED)
        FulfillmentStatus.entries.shouldContainExactly(FulfillmentStatus.UNFULFILLED, FulfillmentStatus.PENDING_SHIPMENT, FulfillmentStatus.SHIPPED, FulfillmentStatus.DELIVERED)
        AfterSaleStatus.entries.shouldContainExactly(AfterSaleStatus.NONE, AfterSaleStatus.PROCESSING, AfterSaleStatus.PARTIALLY_COMPLETED, AfterSaleStatus.COMPLETED)
    }

    test("forward flow advances one dimension at a time and preserves event contracts") {
        val order = testOrder()
        order.confirmStock().shouldBeInstanceOf<Success<Unit>>()
        order.tradeStatus shouldBe TradeStatus.ACTIVE
        order.pay(Price.ofFen(100)).shouldBeInstanceOf<Success<Unit>>()
        order.paymentStatus shouldBe PaymentStatus.PAID
        order.domainEventQueue.last().shouldBeInstanceOf<OrderPaidEvent>()
        order.confirmForShipment().shouldBeInstanceOf<Success<Unit>>()
        order.fulfillmentStatus shouldBe FulfillmentStatus.PENDING_SHIPMENT
        order.ship().shouldBeInstanceOf<Success<Unit>>()
        order.fulfillmentStatus shouldBe FulfillmentStatus.SHIPPED
        order.items.map { it.status }.shouldContainExactly(OrderItemStatus.SHIPPING)
        order.domainEventQueue.last().shouldBeInstanceOf<OrderShippedEvent>()
        order.confirmDelivery().shouldBeInstanceOf<Success<Unit>>()
        order.fulfillmentStatus shouldBe FulfillmentStatus.DELIVERED
        order.complete().shouldBeInstanceOf<Success<Unit>>()
        order.tradeStatus shouldBe TradeStatus.COMPLETED
        order.domainEventQueue.last().shouldBeInstanceOf<OrderCompletedEvent>()
    }

    test("invalid forward operation leaves aggregate snapshot and events unchanged") {
        val order = testOrder()
        val before = listOf(order.tradeStatus, order.paymentStatus, order.fulfillmentStatus, order.afterSaleStatus,
            order.actualPay, order.updateTime, order.items.map { it.status }, order.domainEventQueue.toList())
        val result = order.pay(Price.ofFen(100))
        result.shouldBeInstanceOf<Failure<*>>()
        listOf(order.tradeStatus, order.paymentStatus, order.fulfillmentStatus, order.afterSaleStatus,
            order.actualPay, order.updateTime, order.items.map { it.status }, order.domainEventQueue.toList()) shouldBe before
    }

    test("unpaid cancellation closes trade while buyer cancellation cancels items") {
        val buyerCancel = testOrder(trade = TradeStatus.ACTIVE)
        buyerCancel.cancel(CancellationReason(CancellationCategory.BUYER_CANCELLED, "不需要"))
            .shouldBeInstanceOf<Success<Unit>>()
        buyerCancel.tradeStatus shouldBe TradeStatus.CLOSED
        buyerCancel.paymentStatus shouldBe PaymentStatus.UNPAID
        buyerCancel.afterSaleStatus shouldBe AfterSaleStatus.NONE
        buyerCancel.items.single().status shouldBe OrderItemStatus.CANCELED

        val stockFailure = testOrder()
        stockFailure.markStockInsufficient("库存不足").shouldBeInstanceOf<Success<Unit>>()
        stockFailure.tradeStatus shouldBe TradeStatus.CLOSED
        stockFailure.items.single().status shouldBe OrderItemStatus.NONE
    }
})
