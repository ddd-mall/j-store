package com.jstore.order.domain.order

import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class OrderTerminalStateAtomicityPropertyTest : FunSpec({
    test("completed trade rejects every public modifying behavior atomically") {
        val operations: List<(Order) -> Any> = listOf(
            { it.confirmStock() }, { it.markStockInsufficient("x") }, { it.pay(Price.ofFen(100)) },
            { it.confirmForShipment() }, { it.ship() }, { it.confirmDelivery() }, { it.complete() },
            { it.cancel(CancellationReason(CancellationCategory.BUYER_CANCELLED, "x")) },
            { it.requestRefund(RefundReason(RefundCategory.OTHER, "x"), listOf(OrderItemId(1))) },
            { it.approveRefund(listOf(OrderItemId(1))) }, { it.rejectRefund("x", listOf(OrderItemId(1))) },
        )
        val terminalOrders = listOf<() -> OrderImpl>(
            { testOrder(trade = TradeStatus.COMPLETED, payment = PaymentStatus.PAID,
                fulfillment = FulfillmentStatus.DELIVERED, itemStatuses = listOf(OrderItemStatus.SHIPPING_FINISHED)) },
            { testOrder(trade = TradeStatus.CLOSED, itemStatuses = listOf(OrderItemStatus.CANCELED)) },
            { testOrder(trade = TradeStatus.CLOSED, payment = PaymentStatus.REFUNDED,
                afterSale = AfterSaleStatus.COMPLETED, itemStatuses = listOf(OrderItemStatus.CANCELED)) },
        )
        operations.forEach { operation ->
            terminalOrders.forEach { createOrder ->
                val order = createOrder()
                val before = terminalSnapshot(order)
                operation(order).shouldBeInstanceOf<Failure<*>>()
                terminalSnapshot(order) shouldBe before
            }
        }
    }
})

private fun terminalSnapshot(order: Order) = listOf(
    order.tradeStatus, order.paymentStatus, order.fulfillmentStatus, order.afterSaleStatus,
    order.actualPay, order.updateTime, order.items.map { it.status to it.previousItemStatus },
    order.domainEventQueue.toList(),
)
