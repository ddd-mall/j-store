package com.jstore.order.domain.stock

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.acl.StockServiceACL
import org.springframework.stereotype.Component


@Component
class StockFactory(
    private val snowFlakSequence: SnowFlakSequence,
    private val stockServiceACL: StockServiceACL,
) {
    fun create(cmd: StockPreDeductCmd): List<Stock> {
        return cmd.goodsIdsQuantityMap.map {
            val outerStockId = stockServiceACL.preDeduct(it.key, it.value)
            val stock = Stock(
                id = StockId(snowFlakSequence.nextId().toString()),
                orderId = cmd.orderId,
                goodsId = it.key,
                quantity = it.value,
            )
            stock.preDeduct(outerStockId)
            stock
        }.toList()
    }
}