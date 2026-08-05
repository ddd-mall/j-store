package com.jstore.fulfillment.domain

import com.jstore.common.framework.AggregateRepository

interface FulfillmentOrderRepository : AggregateRepository<FulfillmentOrderId, FulfillmentOrder> {
    fun findByOrderId(orderId: Long): FulfillmentOrder?
}
