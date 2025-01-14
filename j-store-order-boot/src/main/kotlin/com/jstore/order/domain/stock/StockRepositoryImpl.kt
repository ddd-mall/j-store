package com.jstore.com.jstore.order.domain.stock

import com.jstore.com.jstore.order.domain.stock.persistent.StockPOJpaRepository
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockServiceACL
import com.jstore.order.domain.saleorder.SaleOrderId
import com.jstore.order.domain.stock.Stock
import com.jstore.order.domain.stock.StockId
import com.jstore.order.domain.stock.StockRepository
import org.springframework.stereotype.Repository

@Repository
class StockRepositoryImpl(
    private val orderStockRepository: StockPOJpaRepository,
    private val stockServiceACL: StockServiceACL
) : StockRepository {
    override fun findAllByOrderId(orderId: SaleOrderId): List<Stock> {
        return orderStockRepository.findAllByOrderId(orderId.value).map {
            it.toStock(stockServiceACL)
        }
    }

    override fun findByOrderIdAndGoodsId(orderId: SaleOrderId, goodsId: GoodsId): Stock? {
        TODO("Not yet implemented")
    }

    override fun saveBatch(stocks: Collection<Stock>) {
        TODO("Not yet implemented")
    }

    override fun save(entity: Stock): Stock {
        TODO("Not yet implemented")
    }

    override fun findById(id: StockId): Stock? {
        TODO("Not yet implemented")
    }
}