package com.jstore.order.domain.order

import com.jstore.common.framework.event.DomainEvent

class OrderCreatedEvent(
    override val source: Any,
    val order: Order,
) : DomainEvent
