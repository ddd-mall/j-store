package com.jstore.com.jstore.order.domain.stock

import com.jstore.com.jstore.order.domain.stock.persistent.StockPO
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
    private val stockPOJpaRepository: StockPOJpaRepository,
    private val stockServiceACL: StockServiceACL
) : StockRepository {
    override fun findAllByOrderId(orderId: SaleOrderId): List<Stock> {
        return stockPOJpaRepository.findAllByOrderId(orderId.value).map {
            it.toStock(stockServiceACL)
        }
    }

    override fun findByOrderIdAndGoodsId(orderId: SaleOrderId, goodsId: GoodsId): Stock? {

    }

    override fun saveBatch(stocks: Collection<Stock>) : List<Stock> {
        val poList = stocks.map { StockPO(it) }.toList()
        return stockPOJpaRepository.saveAll(poList).map { it.toStock(stockServiceACL) }
    }

    override fun save(entity: Stock): Stock {
        val po = StockPO(entity)
        return stockPOJpaRepository.save(po).toStock(stockServiceACL)
    }

    override fun findById(id: StockId): Stock? {
        return stockPOJpaRepository.findById(id.value).map { it.toStock(stockServiceACL) }.orElse(null)
    }
}