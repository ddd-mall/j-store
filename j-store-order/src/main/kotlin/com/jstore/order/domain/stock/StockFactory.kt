package com.jstore.order.domain.stock

import com.jstore.order.acl.StockServiceACL
import org.springframework.stereotype.Component


@Component
class StockFactory(
    private val stockAclService: StockServiceACL,
) {
    fun create(cmd: com.jstore.order.domain.stock.StockPreDeductCmd): List<Stock> {
        return cmd.goodsIdsQuantityMap.map {
            Stock(
                id = null,
                orderId = cmd.orderId,
                goodsId = it.key,
                amount = it.value,
                stockServiceACL = stockAclService
            )
        }.toList()
    }
}