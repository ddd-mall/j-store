package com.jstore.order.acl.goods

import com.jstore.order.saleorder.properties.Price
import java.math.BigDecimal

interface GoodsService {
    fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo>
}

data class GoodsId(val spuId: Long, val skuId: Long)

data class GoodsInfo(
    val spuId: Long = 0,
    val skuId: Long = 0,
    val version: Long = 0,
    val price: Price = Price(BigDecimal.ZERO)
)