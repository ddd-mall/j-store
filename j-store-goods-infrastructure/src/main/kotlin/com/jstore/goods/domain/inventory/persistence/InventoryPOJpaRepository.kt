package com.jstore.goods.domain.inventory.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 商品库存JPA Repository
 */
@Repository
interface InventoryPOJpaRepository : JpaRepository<InventoryPO, Long> {

    fun findByCommodityCode(commodityCode: Long): InventoryPO?

    fun existsByCommodityCode(commodityCode: Long): Boolean
}

