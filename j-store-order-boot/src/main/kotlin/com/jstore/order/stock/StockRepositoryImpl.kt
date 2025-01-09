package com.jstore.com.jstore.order.stock

import com.jstore.com.jstore.order.stock.persistent.OrderStockPOJpaRepository
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockAclService
import com.jstore.order.saleorder.SaleOrderId
import com.jstore.order.stock.Stock
import com.jstore.order.stock.StockId
import com.jstore.order.stock.StockRepository
import org.springframework.stereotype.Repository

@Repository
class StockRepositoryImpl(
    private val orderStockRepository: OrderStockPOJpaRepository,
    private val stockAclService: StockAclService
) : StockRepository {
    override fun findAllByOrderId(orderId: SaleOrderId): List<Stock> {
        return orderStockRepository.findAllByOrderId(orderId.value).map {
            it.toStock(stockAclService)
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