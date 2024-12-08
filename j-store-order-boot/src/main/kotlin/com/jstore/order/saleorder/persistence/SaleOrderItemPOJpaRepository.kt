package com.jstore.com.jstore.order.saleorder.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SaleOrderItemPOJpaRepository: JpaRepository<SaleOrderItemPO, Long> {
    fun findSaleOrderItemPOSBySaleOrderIdIsIn(saleOrderIds: MutableCollection<Long>): MutableList<SaleOrderItemPO>
}