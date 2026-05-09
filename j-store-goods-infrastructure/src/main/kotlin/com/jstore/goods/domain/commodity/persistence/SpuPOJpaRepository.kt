package com.jstore.goods.domain.commodity.persistence

import com.jstore.goods.domain.commodity.CommodityStatus
import org.springframework.data.jpa.repository.JpaRepository

interface SpuPOJpaRepository : JpaRepository<SpuPO, Long> {

    /** 根据 source_spu_id 和 status 查询草稿副本 */
    fun findBySourceSpuIdAndStatus(sourceSpuId: Long, status: CommodityStatus): SpuPO?
}
