package com.jstore.order.saleorder.event

import com.jstore.common.framework.DomainEvent
import com.jstore.order.saleorder.OrderId

data class SaleOrderCreated(val id: OrderId<Long>): DomainEvent
