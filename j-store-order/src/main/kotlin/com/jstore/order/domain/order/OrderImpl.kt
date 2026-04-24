package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.event.OrderCompletedEvent
import com.jstore.order.domain.order.event.OrderPaidEvent
import com.jstore.order.domain.order.event.OrderShippedEvent
import java.time.LocalDateTime
import java.util.*

/**
 * 订单聚合根实现
 * 封装所有正向流程的状态转移逻辑和领域事件发布
 */
class OrderImpl(
    override val id: OrderId,
    override val buyerInfo: UserInfo,
    private val _items: MutableList<OrderItem>,
    override val shippingAddress: GeoAddressInfo,
    private var _status: OrderStatus,
    override val totalAmount: Price,
    private var _actualPay: Price,
    override val createTime: LocalDateTime = LocalDateTime.now(),
    private var _updateTime: LocalDateTime = LocalDateTime.now(),
) : Order {

    override val domainEventQueue: Queue<DomainEvent> = LinkedList()

    override val items: List<OrderItem> get() = _items.toList()
    override val status: OrderStatus get() = _status
    override val actualPay: Price get() = _actualPay
    override val updateTime: LocalDateTime get() = _updateTime

    override fun pay(paidAmount: Price): Result<Unit, BusinessError> {
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.PAID)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法执行支付"))
        }
        _status = OrderStatus.PAID
        _actualPay = paidAmount
        _updateTime = LocalDateTime.now()
        publishEvent(OrderPaidEvent(orderId = id, paidAmount = paidAmount))
        return Success(Unit)
    }

    override fun confirmForShipment(): Result<Unit, BusinessError> {
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.PENDING_SHIPMENT)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法确认备货"))
        }
        _status = OrderStatus.PENDING_SHIPMENT
        _updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    override fun ship(): Result<Unit, BusinessError> {
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.SHIPPED)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法执行发货"))
        }
        _status = OrderStatus.SHIPPED
        _updateTime = LocalDateTime.now()
        _items.filterIsInstance<OrderItemImpl>().forEach { it.status = OrderItemStatus.SHIPPING }
        publishEvent(OrderShippedEvent(orderId = id))
        return Success(Unit)
    }

    override fun confirmDelivery(): Result<Unit, BusinessError> {
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.DELIVERED)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法确认收货"))
        }
        _status = OrderStatus.DELIVERED
        _updateTime = LocalDateTime.now()
        _items.filterIsInstance<OrderItemImpl>().forEach { it.status = OrderItemStatus.SHIPPING_FINISHED }
        return Success(Unit)
    }

    override fun complete(): Result<Unit, BusinessError> {
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.COMPLETED)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法完成订单"))
        }
        _status = OrderStatus.COMPLETED
        _updateTime = LocalDateTime.now()
        publishEvent(OrderCompletedEvent(orderId = id))
        return Success(Unit)
    }
}
