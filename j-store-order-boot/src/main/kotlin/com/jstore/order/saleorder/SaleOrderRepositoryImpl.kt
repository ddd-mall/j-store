package com.jstore.com.jstore.order.saleorder

import com.jstore.common.framework.Page
import com.jstore.order.saleorder.SaleOrder
import com.jstore.order.saleorder.SaleOrderId
import com.jstore.order.saleorder.SaleOrderRepository

class SaleOrderRepositoryImpl: SaleOrderRepository {
    override fun findByBuyerUserId(uid: Long): List<SaleOrder> {
        TODO("Not yet implemented")
    }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<SaleOrder> {
        TODO("Not yet implemented")
    }

    override fun save(entity: SaleOrder): SaleOrder {
        TODO("Not yet implemented")
    }

    override fun findById(id: SaleOrderId): SaleOrder? {
        TODO("Not yet implemented")
    }

}