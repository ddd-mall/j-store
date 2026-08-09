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
package com.jstore.order.service

import com.jstore.common.errors.BusinessErrorException
import com.jstore.common.framework.messaging.IntegrationMessageHandler
import com.jstore.common.properties.Price
import com.jstore.common.utils.getOrThrow
import com.jstore.contracts.commerce.*
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.SuccessfulRefundItem

class PaymentCapturedOrderHandler(private val orders: OrderUseCase) :
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
            .getOrThrow(::BusinessErrorException)
    }
}

class FulfillmentPreparedOrderHandler(private val orders: OrderUseCase) :
    IntegrationMessageHandler<FulfillmentPreparedIntegrationEvent> {
    override fun handlerId() = "order.fulfillment-prepared.v1"

    override fun handle(message: FulfillmentPreparedIntegrationEvent) {
        orders
            .recordFulfillmentPrepared(OrderId(message.orderId), message.fulfillmentId.toString())
            .getOrThrow(::BusinessErrorException)
    }
}

class FulfillmentDispatchedOrderHandler(private val orders: OrderUseCase) :
    IntegrationMessageHandler<FulfillmentDispatchedIntegrationEvent> {
    override fun handlerId() = "order.fulfillment-dispatched.v1"

    override fun handle(message: FulfillmentDispatchedIntegrationEvent) {
        orders
            .recordShipmentDispatched(OrderId(message.orderId), message.fulfillmentId.toString())
            .getOrThrow(::BusinessErrorException)
    }
}

class FulfillmentDeliveredOrderHandler(private val orders: OrderUseCase) :
    IntegrationMessageHandler<FulfillmentDeliveredIntegrationEvent> {
    override fun handlerId() = "order.fulfillment-delivered.v1"

    override fun handle(message: FulfillmentDeliveredIntegrationEvent) {
        orders
            .recordShipmentDelivered(OrderId(message.orderId), message.fulfillmentId.toString())
            .getOrThrow(::BusinessErrorException)
        orders.completeOrder(OrderId(message.orderId)).getOrThrow(::BusinessErrorException)
    }
}

class PaymentRefundSucceededOrderHandler(
    private val afterSales: AfterSaleUseCase,
    private val orders: OrderUseCase,
) : IntegrationMessageHandler<PaymentRefundSucceededIntegrationEvent> {
    override fun handlerId() = "order.payment-refund-succeeded.v1"

    override fun handle(message: PaymentRefundSucceededIntegrationEvent) {
        afterSales
            .recordRefundSucceeded(
                AfterSaleId(message.afterSaleId),
                message.refundId.toString(),
                message.occurredAt,
            )
            .getOrThrow(::BusinessErrorException)
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
            .getOrThrow(::BusinessErrorException)
    }
}

class PaymentRefundFailedOrderHandler(private val afterSales: AfterSaleUseCase) :
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
            .getOrThrow(::BusinessErrorException)
    }
}
