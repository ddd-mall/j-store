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

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.fulfillment.domain.FulfillmentOrderId
import java.time.Instant

sealed class FulfillmentEvent(
    open val fulfillmentId: FulfillmentOrderId,
    open val orderId: Long,
    override val occurredAt: Instant,
    override val eventId: String,
    override val eventName: String,
    override val eventVersion: Int,
) : DomainEvent {

    override val aggregateType: String = "FulfillmentOrder"
    override val aggregateId: String
        get() = fulfillmentId.value.toString()
}

@DomainEventType(name = "fulfillment.prepared", version = 1)
data class FulfillmentPreparedEvent(
    override val fulfillmentId: FulfillmentOrderId,
    override val orderId: Long,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : FulfillmentEvent(fulfillmentId, orderId, occurredAt, eventId, "fulfillment.prepared", 1)

@DomainEventType(name = "fulfillment.dispatched", version = 1)
data class ShipmentDispatchedEvent(
    override val fulfillmentId: FulfillmentOrderId,
    override val orderId: Long,
    val carrierCode: String,
    val trackingNumber: String,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : FulfillmentEvent(fulfillmentId, orderId, occurredAt, eventId, "fulfillment.dispatched", 1)

@DomainEventType(name = "fulfillment.delivered", version = 1)
data class ShipmentDeliveredEvent(
    override val fulfillmentId: FulfillmentOrderId,
    override val orderId: Long,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : FulfillmentEvent(fulfillmentId, orderId, occurredAt, eventId, "fulfillment.delivered", 1)
