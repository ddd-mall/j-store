package com.jstore.order.stock

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import org.springframework.stereotype.Component



@Component
class StockFactory {
    private val log: Logger = LoggerFactory.getLogger(this::class)
    fun create(cmd: StockPreDeductCmd): List<Stock> {
        log.warn("stock need ")
        return cmd.goodsIdsQuantityMap.map { StockImpl(null, cmd.orderId, it) }.toList()
    }

}