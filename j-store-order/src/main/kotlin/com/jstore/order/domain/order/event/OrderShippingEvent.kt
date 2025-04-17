package com.jstore.order.domain.order.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.order.domain.order.OrderId

class OrderShippingEvent(
    override val source: Any,
    private val orderId: OrderId
) : DomainEvent