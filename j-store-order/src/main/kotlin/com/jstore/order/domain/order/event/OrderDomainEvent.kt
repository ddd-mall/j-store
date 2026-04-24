package com.jstore.order.domain.order.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.OrderId
import java.time.Instant

/**
 * 订单领域事件基类
 * 实现 DomainEvent 接口，以便通过 AgreeGate.publishEvent() 发布
 */
sealed class OrderDomainEvent(
    open val orderId: OrderId,
    open val occurredAt: Instant = Instant.now()
) : DomainEvent {
    override val source: Any get() = orderId
}

/**
 * 库存预扣所需的商品行项信息
 */
data class OrderItemSnapshot(
    val skuId: Long,
    val quantity: Int,
)

/**
 * 订单已创建事件
 * 携带商品行项快照，供库存上下文执行预扣
 */
data class OrderCreatedEvent(
    override val orderId: OrderId,
    val totalAmount: Price,
    val items: List<OrderItemSnapshot>,
    override val occurredAt: Instant = Instant.now()
) : OrderDomainEvent(orderId, occurredAt)

/**
 * 订单已支付事件
 * 携带商品行项快照，供库存上下文执行 confirm（真正扣减）
 */
data class OrderPaidEvent(
    override val orderId: OrderId,
    val paidAmount: Price,
    val items: List<OrderItemSnapshot>,
    override val occurredAt: Instant = Instant.now()
) : OrderDomainEvent(orderId, occurredAt)

/**
 * 订单已发货事件
 */
data class OrderShippedEvent(
    override val orderId: OrderId,
    override val occurredAt: Instant = Instant.now()
) : OrderDomainEvent(orderId, occurredAt)

/**
 * 订单已完成事件
 */
data class OrderCompletedEvent(
    override val orderId: OrderId,
    override val occurredAt: Instant = Instant.now()
) : OrderDomainEvent(orderId, occurredAt)

/**
 * 订单已取消事件（逆向，预留）
 */
data class OrderCancelledEvent(
    override val orderId: OrderId,
    val reason: String = "",
    override val occurredAt: Instant = Instant.now()
) : OrderDomainEvent(orderId, occurredAt)
