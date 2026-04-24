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
 * 订单已创建事件
 */
data class OrderCreatedEvent(
    override val orderId: OrderId,
    val totalAmount: Price,
    override val occurredAt: Instant = Instant.now()
) : OrderDomainEvent(orderId, occurredAt)

/**
 * 订单已支付事件
 */
data class OrderPaidEvent(
    override val orderId: OrderId,
    val paidAmount: Price,
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
