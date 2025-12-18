package com.jstore.order.domain.order

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.item.OrderItem
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.LinkedBlockingQueue


class OrderImpl(
    override val id: OrderId,
    val buyerInfo: UserInfo,
    val orderItemImpls: List<OrderItem>,
    var shippingAddressInfo: GeoAddressInfo,
    var status: OrderStatus,
    var amount: Price,
    var actualPay: Price,
    val createTime: LocalDateTime?,
    val updateTime: LocalDateTime?,
) : Order {
    override val domainEventQueue: Queue<DomainEvent> = LinkedBlockingQueue()


    override fun reserve(): Order {
        TODO("Not yet implemented")
    }


    override fun pay(): Order {
        TODO("Not yet implemented")
    }

    override fun shipping(): Order {

        return this
    }

    override fun complete(): Order {
        TODO("Not yet implemented")
    }

    override fun cancel(): Order {
        return this
    }

    override fun refund(): Order {
        TODO("Not yet implemented")
    }


    override fun confirm(): Order {
        TODO("Not yet implemented")
    }

    override fun undo(): Order {
        TODO("Not yet implemented")
    }

}