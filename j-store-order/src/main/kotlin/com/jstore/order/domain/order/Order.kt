package com.jstore.order.domain.order


import com.jstore.common.framework.AgreeGate
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.event.OrderCanceledEvent
import com.jstore.order.domain.order.event.OrderCreatedEvent
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

data class OrderId(override val value: Long) : Id<Long>(value)
class Order(
    override val id: OrderId,
    val buyerInfo: UserInfo,
    val orderItems: List<OrderItem>,
    var deliveryAddressInfo: GeoAddressInfo,
    var status: OrderStatus,
    var amount: Price,
    var actualPay: Price,
    val createTime: LocalDateTime,
    val updateTime: LocalDateTime
) : AgreeGate<OrderId> {
    override val domainEventQueue: Queue<DomainEvent> = LinkedBlockingQueue()
    fun initial() {
        this.status = OrderStatus.WAIT_PAY
        publishEvent(OrderCreatedEvent(this, this))
    }

    fun cancel() {
        this.status = OrderStatus.CANCELED
        publishEvent(OrderCanceledEvent(this, id))
    }
}


enum class OrderStatus {
    NONE,
    WAIT_PAY,
    WAIT_FOR_SELLER_DELIVERY,
    WAIT_FOR_BUYER_RECEIPT,
    COMPLETE,

    REFUNDING,
    WAIT_FOR_BUYER_DELIVERY,
    WAIT_FOR_SELLER_RECEIPT,
    CANCELED,
    CLOSE,
}



