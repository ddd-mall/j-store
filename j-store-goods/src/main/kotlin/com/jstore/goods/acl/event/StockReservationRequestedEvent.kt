package com.jstore.goods.acl.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import java.time.Instant

/**
 * 库存上下文 ACL 集成事件：请求预扣库存
 * 由外部上下文（如订单）触发，库存上下文只关心"有人要求预扣"这个信号
 */
@DomainEventType(name = "inventory.stock-reservation-requested", version = 1)
data class StockReservationRequestedEvent(
    val orderId: Long,
    val items: List<ReservationItem>,
    override val occurredAt: Instant = Instant.now()
) : ExplicitDomainEvent {
    override val source: Any get() = orderId
    override val eventName: String get() = "inventory.stock-reservation-requested"
    override val eventVersion: Int get() = 1
    override val aggregateType: String get() = "InventoryReservation"
    override val aggregateId: String get() = orderId.toString()
    override val eventId: String get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}

data class ReservationItem(
    val skuId: Long,
    val quantity: Int,
)
