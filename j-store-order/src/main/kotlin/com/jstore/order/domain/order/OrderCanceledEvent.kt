package com.jstore.order.domain.order

import com.jstore.common.framework.event.DomainEvent

class OrderCanceledEvent(
    override val source: Any,
    val orderId: OrderId,
) : DomainEvent
