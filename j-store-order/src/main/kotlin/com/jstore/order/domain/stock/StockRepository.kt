package com.jstore.order.domain.stock

import com.jstore.common.framework.Repository
import com.jstore.order.acl.GoodsId
import com.jstore.order.domain.saleorder.SaleOrderId

interface StockRepository : Repository<StockId, Stock> {
    fun findAllByOrderId(orderId: SaleOrderId): List<Stock>
    fun findByOrderIdAndGoodsId(orderId: SaleOrderId, goodsId: GoodsId): Stock?
    fun saveBatch(stocks: Collection<Stock>)
}