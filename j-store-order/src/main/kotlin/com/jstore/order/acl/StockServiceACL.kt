package com.jstore.order.acl

import java.math.BigDecimal

interface StockServiceACL {
    fun preDeduct(goodsId: GoodsId, quantity: BigDecimal): String
    fun deduct(outerStockId: String): Boolean
    fun rollback(outerStockId: String): Boolean
}
