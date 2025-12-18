package com.jstore.order.domain.order

import com.jstore.common.framework.Repository
import com.jstore.common.framework.Page
import com.jstore.order.domain.order.OrderImpl

interface OrderRepository : Repository<OrderId, OrderImpl> {
    fun findByBuyerUserId(uid: Long): List<OrderImpl>
    fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<OrderImpl>
}