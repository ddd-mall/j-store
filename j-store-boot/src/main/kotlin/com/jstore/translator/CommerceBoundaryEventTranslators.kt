package com.jstore.translator

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.utils.getOrThrow
import com.jstore.fulfillment.domain.FulfillmentItem
import com.jstore.fulfillment.domain.ShippingRecipient
import com.jstore.fulfillment.domain.event.FulfillmentPreparedEvent
import com.jstore.fulfillment.domain.event.ShipmentDeliveredEvent
import com.jstore.fulfillment.domain.event.ShipmentDispatchedEvent
import com.jstore.fulfillment.service.FulfillmentApplicationService
import com.jstore.fulfillment.service.FulfillmentRequest
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.aftersale.event.AfterSaleRefundRequestedEvent
import com.jstore.order.domain.aftersale.event.AfterSaleRefundSucceededEvent
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.SuccessfulRefundItem
import com.jstore.order.domain.order.event.OrderCreatedEvent
import com.jstore.order.domain.order.event.OrderPaidEvent
import com.jstore.order.service.AfterSaleApplicationService
import com.jstore.order.service.OrderService
import com.jstore.payment.domain.payment.PaymentRefundItem
import com.jstore.payment.domain.payment.event.PaymentCapturedEvent
import com.jstore.payment.domain.payment.event.PaymentRefundFailedEvent
import com.jstore.payment.domain.payment.event.PaymentRefundSucceededEvent
import com.jstore.payment.service.PaymentApplicationService
import com.jstore.payment.service.PaymentOrderRequest
import com.jstore.payment.service.PaymentRefundRequest
import org.springframework.stereotype.Component

@Component
class OrderCreatedToPaymentTranslator(private val payments: PaymentApplicationService) :
    DomainEventListener<OrderCreatedEvent> {
    override fun listenerId() = "translator.order-created.to-payment-order.v1"

    override fun onDomainEvent(event: OrderCreatedEvent) {
        payments
            .createForOrder(
                PaymentOrderRequest(
                    event.orderId.value,
                    event.merchantId.value,
                    event.payableAmount,
                    event.currency,
                )
            )
            .getOrThrow()
    }
}

@Component
class PaymentCapturedToOrderTranslator(private val orders: OrderService) :
    DomainEventListener<PaymentCapturedEvent> {
    override fun listenerId() = "translator.payment-captured.to-order.v1"

    override fun onDomainEvent(event: PaymentCapturedEvent) {
        orders
            .recordPaymentCaptured(
                OrderId(event.orderId),
                event.paymentId.value.toString(),
                event.amount,
                event.currency,
                event.occurredAt,
            )
            .getOrThrow()
    }
}

@Component
class OrderPaidToFulfillmentTranslator(
    private val orders: OrderRepository,
    private val fulfillments: FulfillmentApplicationService,
) : DomainEventListener<OrderPaidEvent> {
    override fun listenerId() = "translator.order-paid.to-fulfillment.v1"

    override fun onDomainEvent(event: OrderPaidEvent) {
        val order =
            requireNotNull(orders.findById(event.orderId)) {
                "Order ${event.orderId.value} not found"
            }
        val recipient = order.recipientInfo
        fulfillments
            .createForOrder(
                FulfillmentRequest(
                    orderId = order.id.value,
                    merchantId = order.merchantId.value,
                    recipient =
                        ShippingRecipient(
                            name = recipient.name,
                            phone = recipient.contractInfo.phoneNumber?.value,
                            email = recipient.contractInfo.email,
                            countryCode = recipient.shippingAddress.countryCode.value,
                            districtCode = recipient.shippingAddress.getLeafCode(),
                            detailAddress = recipient.shippingDetailAddress,
                        ),
                    items = order.items.map { FulfillmentItem(it.id.value, it.skuId, it.quantity) },
                )
            )
            .getOrThrow()
    }
}

@Component
class FulfillmentPreparedToOrderTranslator(private val orders: OrderService) :
    DomainEventListener<FulfillmentPreparedEvent> {
    override fun listenerId() = "translator.fulfillment-prepared.to-order.v1"

    override fun onDomainEvent(event: FulfillmentPreparedEvent) {
        orders
            .recordFulfillmentPrepared(OrderId(event.orderId), event.fulfillmentId.value.toString())
            .getOrThrow()
    }
}

@Component
class ShipmentDispatchedToOrderTranslator(private val orders: OrderService) :
    DomainEventListener<ShipmentDispatchedEvent> {
    override fun listenerId() = "translator.shipment-dispatched.to-order.v1"

    override fun onDomainEvent(event: ShipmentDispatchedEvent) {
        orders
            .recordShipmentDispatched(OrderId(event.orderId), event.fulfillmentId.value.toString())
            .getOrThrow()
    }
}

@Component
class ShipmentDeliveredToOrderTranslator(private val orders: OrderService) :
    DomainEventListener<ShipmentDeliveredEvent> {
    override fun listenerId() = "translator.shipment-delivered.to-order.v1"

    override fun onDomainEvent(event: ShipmentDeliveredEvent) {
        orders
            .recordShipmentDelivered(OrderId(event.orderId), event.fulfillmentId.value.toString())
            .getOrThrow()
        orders.completeOrder(OrderId(event.orderId)).getOrThrow()
    }
}

@Component
class AfterSaleRefundRequestedToPaymentTranslator(private val payments: PaymentApplicationService) :
    DomainEventListener<AfterSaleRefundRequestedEvent> {
    override fun listenerId() = "translator.after-sale-refund-requested.to-payment.v1"

    override fun onDomainEvent(event: AfterSaleRefundRequestedEvent) {
        payments
            .requestRefund(
                PaymentRefundRequest(
                    orderId = event.orderId.value,
                    afterSaleId = event.afterSaleId.value,
                    items =
                        event.items.map {
                            PaymentRefundItem(
                                it.orderItemId.value,
                                it.skuId,
                                it.quantity,
                                it.amount,
                            )
                        },
                    amount = event.amount,
                )
            )
            .getOrThrow()
    }
}

@Component
class PaymentRefundSucceededToAfterSaleTranslator(
    private val afterSales: AfterSaleApplicationService
) : DomainEventListener<PaymentRefundSucceededEvent> {
    override fun listenerId() = "translator.payment-refund-succeeded.to-after-sale.v1"

    override fun onDomainEvent(event: PaymentRefundSucceededEvent) {
        afterSales
            .recordRefundSucceeded(
                AfterSaleId(event.afterSaleId),
                event.refundId.value.toString(),
                event.occurredAt,
            )
            .getOrThrow()
    }
}

@Component
class PaymentRefundFailedToAfterSaleTranslator(
    private val afterSales: AfterSaleApplicationService
) : DomainEventListener<PaymentRefundFailedEvent> {
    override fun listenerId() = "translator.payment-refund-failed.to-after-sale.v1"

    override fun onDomainEvent(event: PaymentRefundFailedEvent) {
        afterSales
            .recordRefundFailed(
                AfterSaleId(event.afterSaleId),
                event.refundId.value.toString(),
                event.reason,
                event.occurredAt,
            )
            .getOrThrow()
    }
}

@Component
class AfterSaleRefundSucceededToOrderTranslator(private val orders: OrderService) :
    DomainEventListener<AfterSaleRefundSucceededEvent> {
    override fun listenerId() = "translator.after-sale-refund-succeeded.to-order.v1"

    override fun onDomainEvent(event: AfterSaleRefundSucceededEvent) {
        orders
            .recordRefundSucceeded(
                orderId = event.orderId,
                refundId = event.refundId,
                afterSaleId = event.afterSaleId,
                items =
                    event.items.map {
                        SuccessfulRefundItem(it.orderItemId, it.quantity, it.amount)
                    },
                occurredAt = event.occurredAt,
            )
            .getOrThrow()
    }
}
