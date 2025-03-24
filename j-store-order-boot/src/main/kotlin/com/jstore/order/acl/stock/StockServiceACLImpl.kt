package com.jstore.com.jstore.order.acl.stock

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockServiceACL
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class StockServiceACLImpl(
    private val snowFlakSequence: SnowFlakSequence
) : StockServiceACL {
    private val log: Logger = LoggerFactory.getLogger(this::class)
    override fun preDeduct(goodsId: GoodsId, quantity: BigDecimal) : String {
        log.info("goods: ${goodsId}'s stock pre deducted with total $quantity")
        return snowFlakSequence.nextId().toString()
    }

    override fun confirm(outerStockId: String): Boolean {
        log.info("stock $outerStockId have been deducted")
        return true
    }

    override fun cancel(outerStockId: String): Boolean {
        log.info("stock $outerStockId haven rollback for deduct")
        return true
    }
}