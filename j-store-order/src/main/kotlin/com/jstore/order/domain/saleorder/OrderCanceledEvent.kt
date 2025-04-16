package com.jstore.order.domain.saleorder

import com.jstore.common.framework.event.DomainEvent

class OrderCanceledEvent(
    override val source: Any,
    val saleOrderId: SaleOrderId,
) : DomainEvent
