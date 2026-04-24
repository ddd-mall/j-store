package com.jstore.goods.acl.event

import com.jstore.common.framework.event.DomainEvent
import java.time.Instant

/**
 * 库存上下文 ACL 集成事件：请求释放预扣库存
 */
data class StockReleaseRequestedEvent(
    val orderId: Long,
    val items: List<ReleaseItem>,
    val occurredAt: Instant = Instant.now()
) : DomainEvent {
    override val source: Any get() = orderId
}

data class ReleaseItem(
    val skuId: Long,
)
