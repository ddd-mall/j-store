package com.jstore.order.domain.order

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.item.OrderItem
import java.time.LocalDateTime
import java.util.*


class NormalOrderImpl(
    override val id: OrderId,
    override val buyerInfo: UserInfo,
    override val orderItemImpls: List<OrderItem>,
    override var shippingAddressInfo: GeoAddressInfo,
    override var status: OrderStatus,
    override var amount: Price,
    override var actualPay: Price,
    override val createTime: LocalDateTime?,
    override val updateTime: LocalDateTime?,
) : Order {
    override val domainEventQueue: Queue<DomainEvent> = ArrayDeque<DomainEvent>()


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