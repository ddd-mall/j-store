package com.jstore.goods.domain.commodity.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface GoodsStylePOJpaRepository : JpaRepository<GoodsStylePO, Long> {
    fun findBySpuId(spuId: Long): GoodsStylePO?
}
