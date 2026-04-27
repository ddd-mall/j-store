package com.jstore.goods.domain.commodity.comand

import com.jstore.common.properties.Price
import com.jstore.goods.domain.commodity.Attribute
import com.jstore.goods.domain.commodity.SpuId

data class SkuCreateCmd(
    val spuId: SpuId,
    val skuName: String,
    val attributes: List<Attribute<String, String>>,
    val price: Price,
)
