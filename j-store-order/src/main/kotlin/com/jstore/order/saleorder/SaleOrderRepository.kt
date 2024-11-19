package com.jstore.order.saleorder

import com.jstore.com.jstore.framework.Repository

interface SaleOrderRepository : Repository<OrderId<Long>, SaleOrder> {
    fun findByBuyerUserId(uid: Long):List<SaleOrder> {
        TODO();
    }
}