package com.jstore.order.saleorder

import com.jstore.com.jstore.framework.Repository
import com.jstore.common.framework.Page

interface SaleOrderRepository : Repository<SaleOrderId, SaleOrder> {
    fun findByBuyerUserId(uid: Long): List<SaleOrder>

    fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<SaleOrder>
}