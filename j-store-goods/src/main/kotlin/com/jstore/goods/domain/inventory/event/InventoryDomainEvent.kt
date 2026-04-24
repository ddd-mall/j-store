package com.jstore.goods.domain.inventory.event

import com.jstore.common.framework.event.DomainEvent
import java.time.Instant

/**
 * 库存领域事件基类
 */
sealed class InventoryDomainEvent(
    open val occurredAt: Instant = Instant.now()
) : DomainEvent {
    override val source: Any get() = this::class.simpleName ?: "InventoryEvent"
}

/**
 * 库存预扣成功事件
 */
data class StockReservedEvent(
    val orderId: Long,
    override val occurredAt: Instant = Instant.now()
) : InventoryDomainEvent(occurredAt)

/**
 * 库存预扣失败事件
 */
data class StockReservationFailedEvent(
    val orderId: Long,
    val reason: String,
    override val occurredAt: Instant = Instant.now()
) : InventoryDomainEvent(occurredAt)
