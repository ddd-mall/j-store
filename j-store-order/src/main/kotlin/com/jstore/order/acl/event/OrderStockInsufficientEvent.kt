package com.jstore.order.acl.event

import com.jstore.common.framework.event.DomainEvent
import java.time.Instant

/**
 * 订单上下文 ACL 集成事件：库存不足
 * 订单上下文只关心"库存不够"这个信号
 */
data class OrderStockInsufficientEvent(
    val orderId: Long,
    val reason: String,
    val occurredAt: Instant = Instant.now()
) : DomainEvent {
    override val source: Any get() = orderId
}
