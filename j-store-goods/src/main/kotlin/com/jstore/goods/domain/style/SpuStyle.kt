package com.jstore.goods.domain.style

import com.jstore.common.framework.Entity
import com.jstore.common.framework.Identify
import com.jstore.goods.domain.img.Image
import com.jstore.goods.domain.sku.SkuId

import com.jstore.goods.domain.spu.SpuId

class SpuStyle(
    private val spuId: SpuId,
    private val spuVersion: Long,
    val styleType: SpuStyleType,
    var mainImage: Image,
    var images: List<Image>,
): Entity<SpuStyleId> {
    override fun id(): SpuStyleId {
        return SpuStyleId(spuId = this.spuId, spuVersion = this.spuVersion)
    }
}

class SpuStyleId(val spuId: SpuId, val spuVersion: Long): Identify

enum class SpuStyleType {
    DEFAULT,
}


// ================================= SKU =================================
// -----------------------------------------------------------------------
class SkuStyle(
    private val skuId: SkuId,
    private val skuVersion: Long,

    ): Entity<SkuStyleId> {
    override fun id(): SkuStyleId {
        return SkuStyleId(skuId = skuId, skuVersion = skuVersion)
    }
}

class SkuStyleId(val skuId: SkuId, val skuVersion: Long) : Identify
enum class SkuStyleType {
    DEFAULT
}