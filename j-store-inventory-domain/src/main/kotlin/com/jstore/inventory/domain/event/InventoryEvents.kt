package com.jstore.inventory.domain.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.inventory.domain.StockReservationId
import java.time.Instant

@DomainEventType(name = "inventory.stock-reserved", version = 1)
data class StockReservedEvent(
    val orderId: Long,
    val authorizationIds: List<String>,
    val reservationIds: List<String>,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "inventory.stock-reserved"
    override val eventVersion = 1
    override val aggregateType = "OrderStockReservation"
    override val aggregateId = orderId.toString()
}

@DomainEventType(name = "inventory.stock-reservation-failed", version = 1)
data class StockReservationFailedEvent(
    val orderId: Long,
    val authorizationIds: List<String>,
    val reason: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "inventory.stock-reservation-failed"
    override val eventVersion = 1
    override val aggregateType = "OrderStockReservation"
    override val aggregateId = orderId.toString()
}

@DomainEventType(name = "inventory.stock-reservation-released", version = 1)
data class StockReservationReleasedEvent(
    val reservationId: StockReservationId,
    val orderId: Long,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "inventory.stock-reservation-released"
    override val eventVersion = 1
    override val aggregateType = "StockReservation"
    override val aggregateId = reservationId.value
}
