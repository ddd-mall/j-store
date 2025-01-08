package com.jstore.order.acl

import com.jstore.order.stock.StockId
import java.math.BigDecimal

interface StockAclService {
    fun preDeduct(goodsId: GoodsId, amount: BigDecimal): StockId
    fun deduct(stockId: StockId): Boolean
    fun rollback(stockId: StockId): Boolean
}
