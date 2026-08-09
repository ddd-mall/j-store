/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.translator

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.messaging.IntegrationMessagePublisher
import com.jstore.contracts.commerce.*
import com.jstore.fulfillment.domain.event.FulfillmentPreparedEvent
import com.jstore.fulfillment.domain.event.ShipmentDeliveredEvent
import com.jstore.fulfillment.domain.event.ShipmentDispatchedEvent
import com.jstore.order.domain.aftersale.event.AfterSaleRefundRequestedEvent
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.event.OrderCompletedEvent
import com.jstore.order.domain.order.event.OrderPaidEvent
import com.jstore.order.domain.order.event.OrderStockConfirmedEvent
import com.jstore.payment.domain.payment.event.PaymentCapturedEvent
import com.jstore.payment.domain.payment.event.PaymentRefundFailedEvent
import com.jstore.payment.domain.payment.event.PaymentRefundSucceededEvent
import org.springframework.stereotype.Component

@Component
class OrderStockConfirmedToPaymentTranslator(private val publisher: IntegrationMessagePublisher) :
    DomainEventListener<OrderStockConfirmedEvent> {
    override fun listenerId() = "translator.order-stock-confirmed.to-payment-order.v3"

    override fun onDomainEvent(event: OrderStockConfirmedEvent) {
        publisher.publish(
            CreatePaymentForOrderCommand(
                orderId = event.orderId.value,
                merchantId = event.merchantId.value,
                payableAmountFen = event.payableAmount.fen,
                currency = event.currency,
                sourceMessageId = event.eventId,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}

@Component
class PaymentCapturedIntegrationTranslator(private val publisher: IntegrationMessagePublisher) :
    DomainEventListener<PaymentCapturedEvent> {
    override fun listenerId() = "translator.payment-captured.to-integration.v2"

    override fun onDomainEvent(event: PaymentCapturedEvent) {
        publisher.publish(
            PaymentCapturedIntegrationEvent(
                paymentId = event.paymentId.value,
                orderId = event.orderId,
                merchantId = event.merchantId,
                providerTransactionId = event.providerTransactionId,
                amountFen = event.amount.fen,
                currency = event.currency,
                sourceMessageId = event.eventId,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}

@Component
class OrderPaidToFulfillmentTranslator(
    private val orders: OrderRepository,
    private val publisher: IntegrationMessagePublisher,
) : DomainEventListener<OrderPaidEvent> {
    override fun listenerId() = "translator.order-paid.to-fulfillment.v2"

    override fun onDomainEvent(event: OrderPaidEvent) {
        val order = requireNotNull(orders.findById(event.orderId))
        val recipient = order.recipientInfo
        publisher.publish(
            CreateFulfillmentForOrderCommand(
                orderId = order.id.value,
                merchantId = order.merchantId.value,
                recipient =
                    ContractRecipient(
                        recipient.name,
                        recipient.contractInfo.phoneNumber?.value,
                        recipient.contractInfo.email,
                        recipient.shippingAddress.countryCode.value,
                        recipient.shippingAddress.getLeafCode(),
                        recipient.shippingDetailAddress,
                    ),
                items =
                    order.items.map {
                        ContractItem(
                            skuId = it.skuId,
                            quantity = it.quantity,
                            orderItemId = it.id.value,
                        )
                    },
                sourceMessageId = event.eventId,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}

@Component
class FulfillmentPreparedIntegrationTranslator(private val publisher: IntegrationMessagePublisher) :
    DomainEventListener<FulfillmentPreparedEvent> {
    override fun listenerId() = "translator.fulfillment-prepared.to-integration.v2"

    override fun onDomainEvent(event: FulfillmentPreparedEvent) =
        publisher.publish(
            FulfillmentPreparedIntegrationEvent(
                event.fulfillmentId.value,
                event.orderId,
                event.eventId,
                event.occurredAt,
            )
        )
}

@Component
class ShipmentDispatchedIntegrationTranslator(private val publisher: IntegrationMessagePublisher) :
    DomainEventListener<ShipmentDispatchedEvent> {
    override fun listenerId() = "translator.fulfillment-dispatched.to-integration.v2"

    override fun onDomainEvent(event: ShipmentDispatchedEvent) =
        publisher.publish(
            FulfillmentDispatchedIntegrationEvent(
                event.fulfillmentId.value,
                event.orderId,
                event.eventId,
                event.occurredAt,
            )
        )
}

@Component
class ShipmentDeliveredIntegrationTranslator(private val publisher: IntegrationMessagePublisher) :
    DomainEventListener<ShipmentDeliveredEvent> {
    override fun listenerId() = "translator.fulfillment-delivered.to-integration.v2"

    override fun onDomainEvent(event: ShipmentDeliveredEvent) =
        publisher.publish(
            FulfillmentDeliveredIntegrationEvent(
                event.fulfillmentId.value,
                event.orderId,
                event.eventId,
                event.occurredAt,
            )
        )
}

@Component
class AfterSaleRefundRequestedToPaymentTranslator(
    private val publisher: IntegrationMessagePublisher
) : DomainEventListener<AfterSaleRefundRequestedEvent> {
    override fun listenerId() = "translator.after-sale-refund-requested.to-payment.v2"

    override fun onDomainEvent(event: AfterSaleRefundRequestedEvent) {
        publisher.publish(
            RequestPaymentRefundCommand(
                orderId = event.orderId.value,
                afterSaleId = event.afterSaleId.value,
                items =
                    event.items.map {
                        ContractRefundItem(
                            it.orderItemId.value,
                            it.skuId,
                            it.quantity,
                            it.amount.fen,
                        )
                    },
                amountFen = event.amount.fen,
                sourceMessageId = event.eventId,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}

@Component
class PaymentRefundSucceededIntegrationTranslator(
    private val publisher: IntegrationMessagePublisher
) : DomainEventListener<PaymentRefundSucceededEvent> {
    override fun listenerId() = "translator.payment-refund-succeeded.to-integration.v2"

    override fun onDomainEvent(event: PaymentRefundSucceededEvent) {
        publisher.publish(
            PaymentRefundSucceededIntegrationEvent(
                event.paymentId.value,
                event.refundId.value,
                event.orderId,
                event.afterSaleId,
                event.merchantId,
                event.providerRefundId,
                event.items.map {
                    ContractRefundItem(it.orderItemId, it.skuId, it.quantity, it.amount.fen)
                },
                event.amount.fen,
                event.currency,
                event.eventId,
                event.occurredAt,
            )
        )
    }
}

@Component
class PaymentRefundFailedIntegrationTranslator(private val publisher: IntegrationMessagePublisher) :
    DomainEventListener<PaymentRefundFailedEvent> {
    override fun listenerId() = "translator.payment-refund-failed.to-integration.v2"

    override fun onDomainEvent(event: PaymentRefundFailedEvent) =
        publisher.publish(
            PaymentRefundFailedIntegrationEvent(
                event.paymentId.value,
                event.refundId.value,
                event.orderId,
                event.afterSaleId,
                event.reason,
                event.eventId,
                event.occurredAt,
            )
        )
}

@Component
class OrderCompletedIntegrationTranslator(private val publisher: IntegrationMessagePublisher) :
    DomainEventListener<OrderCompletedEvent> {
    override fun listenerId() = "translator.order-completed.to-integration.v1"

    override fun onDomainEvent(event: OrderCompletedEvent) =
        publisher.publish(
            OrderCompletedIntegrationEvent(
                event.orderId.value,
                event.eventId,
                event.occurredAt,
            )
        )
}
