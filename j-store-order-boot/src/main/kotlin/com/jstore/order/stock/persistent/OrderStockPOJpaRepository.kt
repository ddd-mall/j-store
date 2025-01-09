package com.jstore.com.jstore.order.stock.persistent

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderStockPOJpaRepository : JpaRepository<Long, OrderStockPO> {
    fun findAllByOrderId(orderId: Long): List<OrderStockPO>
    fun findByOrderIdAndSkuId(orderId: Long, skuId: Long): OrderStockPO?
}