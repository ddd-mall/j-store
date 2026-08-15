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
package com.jstore.order.domain.order.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.MerchantId
import com.jstore.order.domain.order.OrderId
import java.time.Instant

sealed class OrderDomainEvent(
    open val orderId: OrderId,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String,
    override val eventName: String,
    override val eventVersion: Int,
) : DomainEvent {

    override val aggregateType: String = "Order"
    override val aggregateId: String
        get() = orderId.value.toString()
}

data class OrderItemSnapshot(
    val spuId: Long,
    val skuId: Long,
    val quantity: Int,
    val catalogSnapshotVersion: Long,
    val unitPrice: Price,
    val offerId: Long = skuId,
    val storeId: Long = 1,
    val offerVersion: Long = 1,
    val fulfillmentNodeId: String = "DEFAULT",
    val channelId: String = "ONLINE",
)

@DomainEventType(name = "order.created", version = 4)
data class OrderCreatedEvent(
    override val orderId: OrderId,
    val merchantId: MerchantId,
    val payableAmount: Price,
    val currency: String,
    val items: List<OrderItemSnapshot>,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : OrderDomainEvent(orderId, occurredAt, eventId, "order.created", 4)

@DomainEventType(name = "order.trade-committed", version = 1)
data class OrderTradeCommittedEvent(
    override val orderId: OrderId,
    val merchantId: MerchantId,
    val payableAmount: Price,
    val currency: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : OrderDomainEvent(orderId, occurredAt, eventId, "order.trade-committed", 1)

@DomainEventType(name = "order.paid", version = 2)
data class OrderPaidEvent(
    override val orderId: OrderId,
    val merchantId: MerchantId,
    val paymentReference: String,
    val paidAmount: Price,
    val currency: String,
    val items: List<OrderItemSnapshot>,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : OrderDomainEvent(orderId, occurredAt, eventId, "order.paid", 2)

@DomainEventType(name = "order.completed")
data class OrderCompletedEvent(
    override val orderId: OrderId,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : OrderDomainEvent(orderId, occurredAt, eventId, "order.completed", 1)

@DomainEventType(name = "order.cancelled")
data class OrderCancelledEvent(
    override val orderId: OrderId,
    val reason: String = "",
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : OrderDomainEvent(orderId, occurredAt, eventId, "order.cancelled", 1)

@DomainEventType(name = "order.cancelled-by-trade", version = 1)
data class OrderCancelledByTradeEvent(
    override val orderId: OrderId,
    val tradeId: Long,
    val orderPlanId: Long,
    val reason: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : OrderDomainEvent(orderId, occurredAt, eventId, "order.cancelled-by-trade", 1)
