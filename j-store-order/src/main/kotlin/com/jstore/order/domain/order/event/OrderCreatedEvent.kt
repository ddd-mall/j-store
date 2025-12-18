package com.jstore.order.domain.order.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.order.domain.order.OrderImpl

class OrderCreatedEvent(
    override val source: Any,
    val orderImpl: OrderImpl,
) : DomainEvent
