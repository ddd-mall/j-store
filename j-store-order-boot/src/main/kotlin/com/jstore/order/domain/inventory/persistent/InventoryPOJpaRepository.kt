package com.jstore.com.jstore.order.domain.inventory.persistent

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface InventoryPOJpaRepository : JpaRepository<InventoryPO, String> {
    fun findAllByOrderId(orderId: Long): List<InventoryPO>
    fun findStockPOByOrderIdAndSpuIdAndSkuId(orderId: Long, spuId: Long, skuId: Long): InventoryPO?

}