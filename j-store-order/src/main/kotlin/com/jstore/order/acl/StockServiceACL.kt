package com.jstore.order.acl

import java.math.BigDecimal

interface StockServiceACL {
    fun preDeduct(goodsId: GoodsId, quantity: BigDecimal): String
    fun confirm(outerStockId: String): Boolean
    fun cancel(outerStockId: String): Boolean
}
