package com.jstore.order.service

import com.jstore.common.framework.messaging.IntegrationMessageHandler
import com.jstore.common.properties.Price
import com.jstore.common.utils.getOrThrow
import com.jstore.contracts.commerce.*
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.SuccessfulRefundItem

class PaymentCapturedOrderHandler(private val orders: OrderService) :
    IntegrationMessageHandler<PaymentCapturedIntegrationEvent> {
    override fun handlerId() = "order.payment-captured.v1"

    override fun handle(message: PaymentCapturedIntegrationEvent) {
        orders
            .recordPaymentCaptured(
                OrderId(message.orderId),
                message.paymentId.toString(),
                Price.ofFen(message.amountFen),
                message.currency,
                message.occurredAt,
            )
            .getOrThrow()
    }
}

class FulfillmentPreparedOrderHandler(private val orders: OrderService) :
    IntegrationMessageHandler<FulfillmentPreparedIntegrationEvent> {
    override fun handlerId() = "order.fulfillment-prepared.v1"

    override fun handle(message: FulfillmentPreparedIntegrationEvent) {
        orders
            .recordFulfillmentPrepared(OrderId(message.orderId), message.fulfillmentId.toString())
            .getOrThrow()
    }
}

class FulfillmentDispatchedOrderHandler(private val orders: OrderService) :
    IntegrationMessageHandler<FulfillmentDispatchedIntegrationEvent> {
    override fun handlerId() = "order.fulfillment-dispatched.v1"

    override fun handle(message: FulfillmentDispatchedIntegrationEvent) {
        orders
            .recordShipmentDispatched(OrderId(message.orderId), message.fulfillmentId.toString())
            .getOrThrow()
    }
}

class FulfillmentDeliveredOrderHandler(private val orders: OrderService) :
    IntegrationMessageHandler<FulfillmentDeliveredIntegrationEvent> {
    override fun handlerId() = "order.fulfillment-delivered.v1"

    override fun handle(message: FulfillmentDeliveredIntegrationEvent) {
        orders
            .recordShipmentDelivered(OrderId(message.orderId), message.fulfillmentId.toString())
            .getOrThrow()
        orders.completeOrder(OrderId(message.orderId)).getOrThrow()
    }
}

class PaymentRefundSucceededOrderHandler(
    private val afterSales: AfterSaleApplicationService,
    private val orders: OrderService,
) : IntegrationMessageHandler<PaymentRefundSucceededIntegrationEvent> {
    override fun handlerId() = "order.payment-refund-succeeded.v1"

    override fun handle(message: PaymentRefundSucceededIntegrationEvent) {
        afterSales
            .recordRefundSucceeded(
                AfterSaleId(message.afterSaleId),
                message.refundId.toString(),
                message.occurredAt,
            )
            .getOrThrow()
        orders
            .recordRefundSucceeded(
                orderId = OrderId(message.orderId),
                refundId = message.refundId.toString(),
                afterSaleId = AfterSaleId(message.afterSaleId),
                items =
                    message.items.map {
                        SuccessfulRefundItem(
                            com.jstore.order.domain.order.OrderItemId(it.orderItemId),
                            it.quantity,
                            Price.ofFen(it.amountFen),
                        )
                    },
                occurredAt = message.occurredAt,
            )
            .getOrThrow()
    }
}

class PaymentRefundFailedOrderHandler(private val afterSales: AfterSaleApplicationService) :
    IntegrationMessageHandler<PaymentRefundFailedIntegrationEvent> {
    override fun handlerId() = "order.payment-refund-failed.v1"

    override fun handle(message: PaymentRefundFailedIntegrationEvent) {
        afterSales
            .recordRefundFailed(
                AfterSaleId(message.afterSaleId),
                message.refundId.toString(),
                message.reason,
                message.occurredAt,
            )
            .getOrThrow()
    }
}
