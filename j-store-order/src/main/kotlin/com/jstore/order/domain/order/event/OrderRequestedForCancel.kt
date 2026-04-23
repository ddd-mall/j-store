package com.jstore.order.domain.order.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.order.domain.order.OrderId

data class OrderRequestedForCancel(
    override val source: Any,
    val orderId: OrderId,
) : DomainEvent
