package com.jstore.order.stock

import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockService
import com.jstore.order.config.ExecutorFactory
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture

class Stock(
    private val goodsId: GoodsId,
    private val stockService: StockService,
    private val executorFactory: ExecutorFactory
) {

    fun deduct(count: BigDecimal): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({ stockService.deduct(goodsId, count) }, executorFactory.get())
    }

}