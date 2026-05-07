package com.jstore.goods.domain.inventory.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import java.time.Instant

/**
 * 库存领域事件基类
 */
sealed class InventoryDomainEvent(
    open val occurredAt: Instant = Instant.now()
) : DomainEvent {
    open override val source: Any get() = this::class.simpleName ?: "InventoryEvent"
}

/**
 * 库存预扣成功事件
 */
@DomainEventType(name = "inventory.stock-reserved", version = 1)
data class StockReservedEvent(
    val orderId: Long,
    override val occurredAt: Instant = Instant.now()
) : InventoryDomainEvent(occurredAt), ExplicitDomainEvent {
    override val source: Any get() = orderId
    override val eventName: String get() = "inventory.stock-reserved"
    override val eventVersion: Int get() = 1
    override val aggregateType: String get() = "InventoryReservation"
    override val aggregateId: String get() = orderId.toString()
    override val eventId: String get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}

/**
 * 库存预扣失败事件
 */
@DomainEventType(name = "inventory.stock-reservation-failed", version = 1)
data class StockReservationFailedEvent(
    val orderId: Long,
    val reason: String,
    override val occurredAt: Instant = Instant.now()
) : InventoryDomainEvent(occurredAt), ExplicitDomainEvent {
    override val source: Any get() = orderId
    override val eventName: String get() = "inventory.stock-reservation-failed"
    override val eventVersion: Int get() = 1
    override val aggregateType: String get() = "InventoryReservation"
    override val aggregateId: String get() = orderId.toString()
    override val eventId: String get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}
