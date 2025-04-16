package com.jstore.order.domain.order.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.order.domain.order.Order

class OrderCreatedEvent(
    override val source: Any,
    val order: Order,
) : DomainEvent
