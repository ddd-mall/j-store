package com.jstore.goods.acl.event

import com.jstore.common.framework.event.DomainEvent
import java.time.Instant

/**
 * 库存上下文 ACL 集成事件：请求确认扣减（预扣 → 真正扣减）
 */
data class StockConfirmRequestedEvent(
    val orderId: Long,
    val items: List<ConfirmItem>,
    val occurredAt: Instant = Instant.now()
) : DomainEvent {
    override val source: Any get() = orderId
}

data class ConfirmItem(
    val skuId: Long,
)
