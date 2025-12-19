package com.jstore.order.domain.order

import com.jstore.common.framework.Repository
import com.jstore.common.framework.Page

interface OrderRepository : Repository<OrderId, NormalOrderImpl> {
    fun findByBuyerUserId(uid: Long): List<NormalOrderImpl>
    fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<NormalOrderImpl>
}