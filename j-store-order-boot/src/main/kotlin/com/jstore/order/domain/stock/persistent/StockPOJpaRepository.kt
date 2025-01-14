package com.jstore.com.jstore.order.domain.stock.persistent

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StockPOJpaRepository : JpaRepository<Long, StockPO> {
    fun findAllByOrderId(orderId: Long): List<StockPO>

}