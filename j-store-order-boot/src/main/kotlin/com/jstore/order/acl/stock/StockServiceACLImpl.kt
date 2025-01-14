package com.jstore.com.jstore.order.acl.stock

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockServiceACL
import com.jstore.order.domain.stock.StockId
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class StockServiceACLImpl(
    private val snowFlakSequence: SnowFlakSequence
) : StockServiceACL {
    private val log: Logger = LoggerFactory.getLogger(this::class)
    override fun preDeduct(goodsId: GoodsId, amount: BigDecimal): StockId {
        log.info("goods: ${goodsId}'s stock pre deducted with total $amount")
        return StockId(snowFlakSequence.nextId().toString())
    }

    override fun deduct(stockId: StockId): Boolean {
        log.info("stock ${stockId.value} have been deducted")
        return true
    }

    override fun rollback(stockId: StockId): Boolean {
        log.info("stock ${stockId.value} haven rollback for deduct")
        return true
    }
}