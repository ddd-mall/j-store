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
    override val source: Any get() = fulfillmentId
    override val aggregateType: String = "FulfillmentOrder"
    override val aggregateId: String get() = fulfillmentId.value.toString()
    override val eventName: String get() = this::class.java.getAnnotation(DomainEventType::class.java).name
    override val eventVersion: Int get() = this::class.java.getAnnotation(DomainEventType::class.java).version
    override val eventId: String get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
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
