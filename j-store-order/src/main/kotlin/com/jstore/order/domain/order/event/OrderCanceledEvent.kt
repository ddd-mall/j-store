package com.jstore.order.domain.order.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.order.domain.order.OrderId

class OrderCanceledEvent(
    override val source: Any,
    val orderId: OrderId,
) : DomainEvent
