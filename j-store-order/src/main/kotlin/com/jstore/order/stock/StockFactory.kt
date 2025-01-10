package com.jstore.order.stock

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.order.acl.StockAclService
import org.springframework.lang.Nullable
import org.springframework.stereotype.Component


@Component
class StockFactory(
    @Nullable
    private val stockAclService: StockAclService,
) {
    private val log: Logger = LoggerFactory.getLogger(this::class)
    fun create(cmd: StockPreDeductCmd): List<Stock> {

        return cmd.goodsIdsQuantityMap.map {
            StockImpl(
                id = null,
                orderId = cmd.orderId,
                goodsId = it.key,
                amount = it.value,
                stockAclService = stockAclService
            )
        }.toList()
    }

}