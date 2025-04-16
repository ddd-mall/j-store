package com.jstore.order.service.acl

import com.jstore.common.properties.Price

interface GoodsService {
    fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo>
}

data class GoodsId(val spuId: Long, val skuId: Long)

data class GoodsInfo(
    val id: GoodsId,
    val version: Long,
    val price: Price
)