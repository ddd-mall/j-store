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
