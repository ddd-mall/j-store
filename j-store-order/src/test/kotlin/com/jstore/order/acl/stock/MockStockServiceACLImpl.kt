package com.jstore.order.acl.stock

import com.jstore.common.logging.LoggerFactory
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockServiceACL
import com.jstore.order.config.TestBeanConfig.snowFlakSequence
import com.jstore.order.domain.stock.StockErrors
import com.jstore.order.domain.stock.StockErrors.StockResourceNotFound
import com.jstore.order.domain.stock.StockStatus
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class MockStockServiceACLImpl : StockServiceACL {
    private val stockMap: ConcurrentHashMap<GoodsId, AtomicReference<BigDecimal>> = ConcurrentHashMap()
    private val stockPOMap: ConcurrentHashMap<String, StockPO> = ConcurrentHashMap()

    private val log = LoggerFactory.getLogger(this::class)

    init {
        stockMap[GoodsId(1, 1)] = AtomicReference(BigDecimal(2))
        stockMap[GoodsId(2, 2)] = AtomicReference(BigDecimal(1))
    }

    override fun preDeduct(goodsId: GoodsId, quantity: BigDecimal): String {
        stockMap[goodsId]?.let { stock ->
            if (stock.get() < quantity) {
                throw StockErrors.StockInsufficient.msg("the corresponding stocks of $goodsId not enough")
            }
            synchronized(stock) {
                if (stock.get() < quantity) {
                    throw StockErrors.StockInsufficient.msg("the corresponding stocks of $goodsId not enough")
                }
                val afterSub = stock.accumulateAndGet(quantity) { pre, sub ->
                    pre.subtract(sub)
                }
                if (afterSub.toDouble() >= 0) {
                    stock.compareAndSet(stock.get(), afterSub)
                } else {
                    throw StockErrors.StockInsufficient.msg("failed to pre deduct $goodsId corresponding stocks")
                }
                log.info("goods $goodsId's stock has been pre deducted with quantity $quantity, remind $afterSub")
            }
        } ?: throw StockResourceNotFound.msg("$goodsId corresponding stocks not found")

        val outerStockId = snowFlakSequence.nextId().toString()
        stockPOMap[outerStockId] = StockPO(
            stockId = outerStockId,
            goodsId = goodsId,
            quantity = quantity,
            status = StockStatus.PRE_DEDUCTED
        )
        return outerStockId
    }

    override fun confirm(outerStockId: String): Boolean {

        val stockPO = stockPOMap[outerStockId] ?:
            throw StockResourceNotFound.msg("$outerStockId corresponding stock not found")
        stockPO.status = StockStatus.DEDUCTED
        stockPOMap[outerStockId] = stockPO
        return true
    }

    override fun cancel(outerStockId: String): Boolean {

        val stockPO = stockPOMap[outerStockId] ?: return true
        when (stockPO.status) {
            StockStatus.CREATED -> return true
            StockStatus.PRE_DEDUCTED -> {
                val atomicReference = stockMap[stockPO.goodsId] ?: throw StockResourceNotFound
                val stockQuantity = atomicReference.get()
                atomicReference.compareAndSet(stockQuantity, stockQuantity.add(stockPO.quantity))
                stockMap[stockPO.goodsId] = atomicReference
                return true
            }
            StockStatus.DEDUCTED -> {
                stockPO.status = StockStatus.PRE_DEDUCTED
                stockPOMap[outerStockId] = stockPO
                return true
            }
        }
    }

    private class StockPO(
        val stockId: String,
        val goodsId: GoodsId,
        val quantity: BigDecimal,
        var status: StockStatus
    ) {

    }
}