package com.jstore.goods.acl.event

import com.jstore.common.framework.event.DomainEvent
import java.time.Instant

/**
 * 库存上下文 ACL 集成事件：请求预扣库存
 * 由外部上下文（如订单）触发，库存上下文只关心"有人要求预扣"这个信号
 */
data class StockReservationRequestedEvent(
    val orderId: Long,
    val items: List<ReservationItem>,
    val occurredAt: Instant = Instant.now()
) : DomainEvent {
    override val source: Any get() = orderId
}

data class ReservationItem(
    val skuId: Long,
    val quantity: Int,
)
