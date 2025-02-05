package com.jstore.com.jstore.order.domain.stock.persistent

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StockPOJpaRepository : JpaRepository<StockPO, String> {
    fun findAllByOrderId(orderId: Long): List<StockPO>
    fun findStockPOByOrderIdAndSpuIdAndSkuId(orderId: Long, spuId: Long, skuId: Long): StockPO?

}