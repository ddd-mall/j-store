package com.jstore.order.domain.stock

import com.jstore.order.acl.GoodsId
import com.jstore.order.domain.saleorder.SaleOrderId
import com.jstore.order.framwork.AbstractMockRepository

class MockStockRepositoryImpl : StockRepository, AbstractMockRepository<StockId, Stock>() {


    override fun findAllByOrderId(orderId: SaleOrderId): List<Stock> {
        return super.objList.filter { it.orderId == orderId }
    }

    override fun findByOrderIdAndGoodsId(orderId: SaleOrderId, goodsId: GoodsId): Stock? {
        return super.objList.find { it.orderId == orderId && it.goodsId == goodsId }
    }

    override fun saveBatch(stocks: Collection<Stock>): List<Stock> {
        return stocks.map(::save)
    }



    override fun nextId(): StockId {
        throw IllegalAccessError()
    }

    override fun copyAnEntity(nextId: StockId, entity: Stock): Stock {
        return Stock(
            id = nextId,
            orderId = entity.orderId,
            goodsId = entity.goodsId,
            amount = entity.amount,
            currentStatus = entity.currentStatus,
            stockServiceACL = entity.stockServiceACL,
            lastStatus = entity.lastStatus,
        )
    }
}