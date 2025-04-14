package com.jstore.order.domain.saleorder

import com.jstore.common.framework.event.DomainEvent

class SaleOrderCreatedEvent(
    val order: SaleOrder,
    override val source: Any,
) : DomainEvent
