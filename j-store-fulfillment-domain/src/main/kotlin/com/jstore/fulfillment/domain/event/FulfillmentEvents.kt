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
package com.jstore.fulfillment.domain.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import com.jstore.fulfillment.domain.FulfillmentOrderId
import java.time.Instant

sealed class FulfillmentEvent(
    open val fulfillmentId: FulfillmentOrderId,
    open val orderId: Long,
    override val occurredAt: Instant,
) : ExplicitDomainEvent {
    override val source: Any
        get() = fulfillmentId

    override val aggregateType: String = "FulfillmentOrder"
    override val aggregateId: String
        get() = fulfillmentId.value.toString()

    override val eventName: String
        get() = this::class.java.getAnnotation(DomainEventType::class.java).name

    override val eventVersion: Int
        get() = this::class.java.getAnnotation(DomainEventType::class.java).version

    override val eventId: String
        get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}

@DomainEventType(name = "fulfillment.prepared", version = 1)
data class FulfillmentPreparedEvent(
    override val fulfillmentId: FulfillmentOrderId,
    override val orderId: Long,
    override val occurredAt: Instant,
) : FulfillmentEvent(fulfillmentId, orderId, occurredAt)

@DomainEventType(name = "fulfillment.dispatched", version = 1)
data class ShipmentDispatchedEvent(
    override val fulfillmentId: FulfillmentOrderId,
    override val orderId: Long,
    val carrierCode: String,
    val trackingNumber: String,
    override val occurredAt: Instant,
) : FulfillmentEvent(fulfillmentId, orderId, occurredAt)

@DomainEventType(name = "fulfillment.delivered", version = 1)
data class ShipmentDeliveredEvent(
    override val fulfillmentId: FulfillmentOrderId,
    override val orderId: Long,
    override val occurredAt: Instant,
) : FulfillmentEvent(fulfillmentId, orderId, occurredAt)
