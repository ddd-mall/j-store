package com.jstore.order.domain.order.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import com.jstore.order.domain.order.RefundReason
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

/**
 * 订单退款申请事件
 * 携带退款金额、退款原因、是否需要退货、退款行项 ID 列表
 */
data class OrderRefundRequestedEvent(
    override val orderId: OrderId,
    val refundAmount: Price,
    val reason: RefundReason,
    val requireReturn: Boolean,
    val refundItemIds: List<OrderItemId>,
    override val occurredAt: Instant = Instant.now()
) : OrderDomainEvent(orderId, occurredAt)

/**
 * 订单退款批准事件
 * 携带退款金额、被批准的行项 ID 列表和是否需要退货标记
 * requireReturn = false 时可直接释放库存；true 时需等退货入库
 */
data class OrderRefundApprovedEvent(
    override val orderId: OrderId,
    val refundAmount: Price,
    val approvedItemIds: List<OrderItemId>,
    val requireReturn: Boolean,
    override val occurredAt: Instant = Instant.now()
) : OrderDomainEvent(orderId, occurredAt)

/**
 * 订单退款拒绝事件
 * 携带拒绝原因和被拒绝的行项 ID 列表
 */
data class OrderRefundRejectedEvent(
    override val orderId: OrderId,
    val rejectReason: String,
    val rejectedItemIds: List<OrderItemId>,
    override val occurredAt: Instant = Instant.now()
) : OrderDomainEvent(orderId, occurredAt)
