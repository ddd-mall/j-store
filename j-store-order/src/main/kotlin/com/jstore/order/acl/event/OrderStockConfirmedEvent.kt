package com.jstore.order.acl.event

import com.jstore.common.framework.event.DomainEvent
import java.time.Instant

/**
 * 订单上下文 ACL 集成事件：库存预扣成功
 * 订单上下文只关心"库存已确认"这个信号，不关心库存内部如何实现
 */
data class OrderStockConfirmedEvent(
    val orderId: Long,
    val occurredAt: Instant = Instant.now()
) : DomainEvent {
    override val source: Any get() = orderId
}
