package com.jstore.order.domain.stock

import com.jstore.order.acl.StockServiceACL
import org.springframework.lang.Nullable
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component


@Component
class StockFactory(
    private val stockAclService: StockServiceACL,
    @Nullable
    private val businessExecutor: ThreadPoolTaskExecutor
) {
    fun create(cmd: StockPreDeductCmd): List<Stock> {
        return cmd.goodsIdsQuantityMap.map {
            Stock(
                id = null,
                orderId = cmd.orderId,
                goodsId = it.key,
                amount = it.value,
                stockServiceACL = stockAclService,
                executor = businessExecutor
            )
        }.toList()
    }
}