package com.jstore.goods.acl.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import java.time.Instant

/** 库存上下文 ACL 集成事件：请求确认扣减（预扣 → 真正扣减） */
@DomainEventType(name = "inventory.stock-confirm-requested", version = 1)
data class StockConfirmRequestedEvent(
    val orderId: Long,
    val items: List<ConfirmItem>,
    override val occurredAt: Instant = Instant.now(),
) : ExplicitDomainEvent {
    override val source: Any
        get() = orderId

    override val eventName: String
        get() = "inventory.stock-confirm-requested"

    override val eventVersion: Int
        get() = 1

    override val aggregateType: String
        get() = "InventoryReservation"

    override val aggregateId: String
        get() = orderId.toString()

    override val eventId: String
        get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}

data class ConfirmItem(val skuId: Long)
