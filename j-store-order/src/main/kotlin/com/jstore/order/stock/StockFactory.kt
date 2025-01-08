package com.jstore.order.stock

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.order.acl.StockAclService
import org.springframework.stereotype.Component


@Component
class StockFactory(
    private val stockAclService: StockAclService,
) {
    private val log: Logger = LoggerFactory.getLogger(this::class)
    fun create(cmd: StockPreDeductCmd): List<Stock> {

        return cmd.goodsIdsQuantityMap.map { StockImpl(null, cmd.orderId, it.key, it.value, stockAclService) }.toList()
    }

}