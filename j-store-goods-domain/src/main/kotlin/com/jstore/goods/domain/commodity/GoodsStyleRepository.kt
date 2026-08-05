package com.jstore.goods.domain.commodity

import com.jstore.common.framework.AggregateRepository

interface GoodsStyleRepository : AggregateRepository<GoodsStyleId, GoodsStyle> {
    fun findBySpuId(spuId: SpuId): GoodsStyle?
}
