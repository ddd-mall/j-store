package com.jstore.order.domain.stock

import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.order.acl.GoodsId
import com.jstore.order.domain.saleorder.SaleOrderId
import org.springframework.stereotype.Component
import java.math.BigDecimal

class StockPreDeductCmd(
    val orderId: SaleOrderId,
    val goodsIdsQuantityMap: Map<GoodsId, BigDecimal>,
)

@Component
open class StockPreDeductHandler(
    private val stockRepository: StockRepository,
    private val stockFactory: StockFactory,
) {
    private val log: Logger = LoggerFactory.getLogger(this::class)


    open fun handle(cmd: StockPreDeductCmd) {
        try {
            val stockList = stockRepository.findAllByOrderId(cmd.orderId).let { find ->
                if (find.any { stock -> cmd.goodsIdsQuantityMap.keys.contains(stock.goodsId) }) {
                    throw CommonErrors.ILLEGAL_STATE.msg("order ${cmd.orderId}'s stock already exists")
                }
                find.ifEmpty {
                    stockFactory.create(cmd)
                }
            }
            stockRepository.saveBatch(stockList)
        } catch (e: Exception) {
            log.error("error occurred when pre deduct stock", e)
            throw StockErrors.StockInsufficient.msgAndCause("order ${cmd.orderId}'s stock pre deduct failed", e)
        }

        log.info("order ${cmd.orderId}'s stock pre deduct success")
    }


}
