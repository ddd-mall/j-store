package com.jstore.order.domain.order

import com.jstore.common.framework.Repository
import com.jstore.common.framework.Page

interface OrderRepository : Repository<OrderId, Order> {
    fun findByBuyerUserId(uid: Long): List<Order>
    fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order>
}