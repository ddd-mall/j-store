package com.jstore.fulfillment.domain

import com.jstore.common.framework.Repository

interface FulfillmentOrderRepository : Repository<FulfillmentOrderId, FulfillmentOrder> {
    fun findByOrderId(orderId: Long): FulfillmentOrder?
}
