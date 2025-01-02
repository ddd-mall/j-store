package com.jstore.goods

import com.jstore.common.properties.Id
import com.jstore.common.properties.Price


class Spu(
    val spuId: SpuId?,
    var spuName: String,
    var skuList: List<Sku>,
    var goodsClass: GoodsClass,
    val version: Long? = null
) {

}



class Sku(
    val skuId: SkuId?,
    val name: String,
    val specifications: List<Specification>,
    val unitPrice: Price,
    val version: Long = 0,
    val style: SkuStyle,
) {

}

data class SpuId(override val value: Long): Id<Long>(value)
data class SkuId(override val value: Long): Id<Long>(value)
class SkuStyle

class Specification(
    val name: String,
    val value: String,
    val style: SpecificationStyle
) {

}

/**
 * 规格的样式
 */
class SpecificationStyle

enum class GoodsClass {

}

