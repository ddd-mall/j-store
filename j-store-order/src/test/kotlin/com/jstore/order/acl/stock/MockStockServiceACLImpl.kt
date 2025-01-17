package com.jstore.order.acl.stock

import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.LoggerFactory
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockServiceACL
import com.jstore.order.config.TestBeanConfig.snowFlakSequence
import com.jstore.order.domain.stock.StockId
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class MockStockServiceACLImpl : StockServiceACL {
    private val stockMap: ConcurrentHashMap<GoodsId, AtomicReference<BigDecimal>> = ConcurrentHashMap()
    private val preDeductedStock: ConcurrentHashMap<StockId, BigDecimal> = ConcurrentHashMap()
    private val deductedStock: ConcurrentHashMap<StockId, BigDecimal> = ConcurrentHashMap()

    private val log = LoggerFactory.getLogger(this::class)
    override fun preDeduct(goodsId: GoodsId, amount: BigDecimal): StockId {
        stockMap[goodsId]?.let { stock ->
            if (stock.get() < amount) {
                throw CommonErrors.ILLEGAL_STATE.to("$goodsId corresponding stocks not enough")
            }
            synchronized(stock) {
                if (stock.get() < amount) {
                    throw CommonErrors.ILLEGAL_STATE.to("$goodsId corresponding stocks not enough")
                }
                val afterSub = stock.accumulateAndGet(amount) { pre, sub ->
                    pre.subtract(sub)
                }
                if (afterSub.toDouble() >= 0) {
                    stock.compareAndSet(stock.get(), afterSub)
                } else {
                    throw CommonErrors.INTERNAL_ERROR.to("failed to pre deduct $goodsId corresponding stocks")
                }
            }
        } ?: throw CommonErrors.RESOURCE_NOT_FOUND.to("$goodsId corresponding stocks not found")

        val stockId = StockId(snowFlakSequence.nextId().toString())
        preDeductedStock[stockId] = amount
        log.info("goods $goodsId have been pre deduct with amount $amount")
        return stockId
    }

    override fun deduct(stockId: StockId): Boolean {

        val stock = preDeductedStock.remove(stockId)
            ?: throw CommonErrors.ILLEGAL_STATE.to("$stockId corresponding stock not found")
        deductedStock.putIfAbsent(stockId, stock)?.let {
            throw CommonErrors.ILLEGAL_STATE.to("$stockId corresponding stock has been deducted ")
        }
        return true
    }

    override fun rollback(stockId: StockId): Boolean {
        TODO("Not yet implemented")
    }
}