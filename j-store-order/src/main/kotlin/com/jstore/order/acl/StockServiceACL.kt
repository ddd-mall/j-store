package com.jstore.order.acl

import java.math.BigDecimal

interface StockServiceACL {
    fun preDeduct(goodsId: GoodsId, quantity: BigDecimal): String
    fun deduct(stockId: String): Boolean
    fun rollback(stockId: String): Boolean
}
