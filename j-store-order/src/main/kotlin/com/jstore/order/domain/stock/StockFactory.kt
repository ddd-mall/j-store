package com.jstore.order.domain.stock

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.acl.StockServiceACL
import org.springframework.lang.Nullable
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component


@Component
class StockFactory(
    private val stockAclService: StockServiceACL,
    @Nullable
    private val businessExecutor: ThreadPoolTaskExecutor,
    private val snowFlakSequence: SnowFlakSequence
) {
    fun create(cmd: StockPreDeductCmd): List<Stock> {
        return cmd.goodsIdsQuantityMap.map {
            Stock(
                id = StockId(snowFlakSequence.nextId().toString()),
                orderId = cmd.orderId,
                goodsId = it.key,
                quantity = it.value,
                stockServiceACL = stockAclService,
                executor = businessExecutor
            )
        }.toList()
    }
}