package com.jstore.order.domain.order


import com.jstore.common.framework.AgreeGate
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.event.OrderCanceledEvent
import com.jstore.order.domain.order.event.OrderCreatedEvent
import com.jstore.order.domain.order.event.OrderShippingEvent
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

/**
 * TODO: 如果不同类型的订单有不同的发货逻辑，这里应该将order抽象一层接口
 */
data class OrderId(override val value: Long) : Id<Long>(value)
class Order(
    override val id: OrderId,
    val buyerInfo: UserInfo,
    val orderItems: List<OrderItem>,
    var deliveryAddressInfo: GeoAddressInfo,
    var status: OrderStatus,
    var amount: Price,
    var actualPay: Price,
    val createTime: LocalDateTime?,
    val updateTime: LocalDateTime?
) : AgreeGate<OrderId> {
    override val domainEventQueue: Queue<DomainEvent> = LinkedBlockingQueue()
    fun initial() {
        this.status = OrderStatus.WAIT_PAY
        publishEvent(OrderCreatedEvent(this, this))
    }

    fun cancel() {
        if (status == OrderStatus.CANCELED) {
            return
        }
        status = OrderStatus.CANCELED
        orderItems.forEach(OrderItem::cancel)
        publishEvent(OrderCanceledEvent(this, id))
    }

    fun sellerShipping() {
        if (status == OrderStatus.SELLER_SHIPPING) {
            return
        }
        if (status != OrderStatus.WAIT_FOR_SELLER_SHIPPING) {
            throw OrderErrors.ILLEGAL_STATE
        }
        orderItems.forEach(OrderItem::shipping)
        status = OrderStatus.SELLER_SHIPPING
        publishEvent(OrderShippingEvent(this, id))
    }
}


enum class OrderStatus {
    NONE,
    WAIT_PAY,
    WAIT_FOR_SELLER_SHIPPING,
    SELLER_SHIPPING,
    COMPLETE,

    REFUNDING,
    WAIT_FOR_BUYER_SHIPPING,
    BUYER_SHIPPING,
    CANCELED,
    CLOSE,
}



