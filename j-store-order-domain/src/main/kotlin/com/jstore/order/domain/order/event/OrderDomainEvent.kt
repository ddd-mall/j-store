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

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.MerchantId
import com.jstore.order.domain.order.OrderId
import java.time.Instant

sealed class OrderDomainEvent(
    open val orderId: OrderId,
    override val occurredAt: Instant = Instant.now(),
) : ExplicitDomainEvent {
    override val source: Any
        get() = orderId

    override val eventName: String
        get() = this::class.java.getAnnotation(DomainEventType::class.java).name

    override val eventVersion: Int
        get() = this::class.java.getAnnotation(DomainEventType::class.java).version

    override val aggregateType: String = "Order"
    override val aggregateId: String
        get() = orderId.value.toString()

    override val eventId: String
        get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}

data class OrderItemSnapshot(val skuId: Long, val quantity: Int)

@DomainEventType(name = "order.created", version = 2)
data class OrderCreatedEvent(
    override val orderId: OrderId,
    val merchantId: MerchantId,
    val payableAmount: Price,
    val currency: String,
    val items: List<OrderItemSnapshot>,
    override val occurredAt: Instant = Instant.now(),
) : OrderDomainEvent(orderId, occurredAt)

@DomainEventType(name = "order.paid", version = 2)
data class OrderPaidEvent(
    override val orderId: OrderId,
    val merchantId: MerchantId,
    val paymentReference: String,
    val paidAmount: Price,
    val currency: String,
    val items: List<OrderItemSnapshot>,
    override val occurredAt: Instant = Instant.now(),
) : OrderDomainEvent(orderId, occurredAt)

@DomainEventType(name = "order.completed")
data class OrderCompletedEvent(
    override val orderId: OrderId,
    override val occurredAt: Instant = Instant.now(),
) : OrderDomainEvent(orderId, occurredAt)

@DomainEventType(name = "order.cancelled")
data class OrderCancelledEvent(
    override val orderId: OrderId,
    val reason: String = "",
    override val occurredAt: Instant = Instant.now(),
) : OrderDomainEvent(orderId, occurredAt)
