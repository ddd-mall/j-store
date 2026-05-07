package com.jstore.order.acl.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import java.time.Instant

/**
 * 订单上下文 ACL 集成事件：库存预扣成功
 * 订单上下文只关心"库存已确认"这个信号，不关心库存内部如何实现
 */
@DomainEventType(name = "order.stock-confirmed", version = 1)
data class OrderStockConfirmedEvent(
    val orderId: Long,
    override val occurredAt: Instant = Instant.now()
) : ExplicitDomainEvent {
    override val source: Any get() = orderId
    override val eventName: String get() = "order.stock-confirmed"
    override val eventVersion: Int get() = 1
    override val aggregateType: String get() = "Order"
    override val aggregateId: String get() = orderId.toString()
    override val eventId: String get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}
