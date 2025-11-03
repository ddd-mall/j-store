package com.jstore.goods.domain.commodity.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * SKU JPA Repository
 */
@Repository
interface SkuPOJpaRepository : JpaRepository<SkuPO, Long> {

    fun findBySkuId(skuId: Long): SkuPO?

    fun findBySpuId(spuId: Long): List<SkuPO>

    fun findByCommodityCode(commodityCode: Long): SkuPO?
}

