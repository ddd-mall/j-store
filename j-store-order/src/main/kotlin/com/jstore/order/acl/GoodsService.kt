package com.jstore.order.acl

import com.jstore.common.properties.Price

interface GoodsService {
    fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo>
}

data class GoodsId(val spuId: Long, val skuId: Long)

data class GoodsInfo(
    val id: GoodsId,
    val merchantId: Long,
    val snapshotVersion: Long,
    val spuName: String,
    val skuName: String,
    val attributes: List<Pair<String, String>>,
    val price: Price
)
