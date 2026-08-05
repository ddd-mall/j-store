package com.jstore.goods.domain.commodity

import com.jstore.common.framework.Repository

interface GoodsStyleRepository : Repository<GoodsStyleId, GoodsStyle> {
    fun findBySpuId(spuId: SpuId): GoodsStyle?
}
