package com.jstore.goods.domain.inventory.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import java.time.Instant

/** 库存预扣成功事件 */
@DomainEventType(name = "inventory.stock-reserved", version = 1)
data class StockReservedEvent(
    val orderId: Long,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {

    override val eventName: String
        get() = "inventory.stock-reserved"

    override val eventVersion: Int
        get() = 1

    override val aggregateType: String
        get() = "InventoryReservation"

    override val aggregateId: String
        get() = orderId.toString()

}

/** 库存预扣失败事件 */
@DomainEventType(name = "inventory.stock-reservation-failed", version = 1)
data class StockReservationFailedEvent(
    val orderId: Long,
    val reason: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {

    override val eventName: String
        get() = "inventory.stock-reservation-failed"

    override val eventVersion: Int
        get() = 1

    override val aggregateType: String
        get() = "InventoryReservation"

    override val aggregateId: String
        get() = orderId.toString()

}
