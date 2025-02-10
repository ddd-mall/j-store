package com.jstore.goods.domain.spu


import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price


class Spu(
    private val id: SpuId,
    val spuName: String,
    val goodsCategory: GoodsCategory,
): Entity<SpuId> {
    override fun id(): SpuId {
        return id
    }
}


class SpuId(override val value: Long) : Id<Long>(value)


enum class GoodsCategory {
    DEFAULT
}



