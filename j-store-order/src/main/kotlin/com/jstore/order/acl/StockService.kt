package com.jstore.order.acl

import java.math.BigDecimal

interface StockService {
    fun deduct(goodsId: GoodsId, count: BigDecimal): Boolean
}