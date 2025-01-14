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
    private val stockRepository: com.jstore.order.domain.stock.StockRepository,
    private val stockFactory: com.jstore.order.domain.stock.StockFactory
) {
    private val log: Logger = LoggerFactory.getLogger(this::class)


    open fun handle(cmd: com.jstore.order.domain.stock.StockPreDeductCmd) {

        val stockList = stockRepository.findAllByOrderId(cmd.orderId).let {
            if (it.any { stock -> cmd.goodsIdsQuantityMap.keys.contains(stock.goodsId) }) {
                throw CommonErrors.ILLEGAL_STATE.to("order ${cmd.orderId}'s stock already exists")
            }
            it.ifEmpty {
                stockFactory.create(cmd)
            }
        }


        try {
            stockList.forEach(com.jstore.order.domain.stock.Stock::preDeduct)
            stockList.let(stockRepository::saveBatch)
        } catch (e: Exception) {
            stockList.forEach(com.jstore.order.domain.stock.Stock::rollback)
            throw CommonErrors.INTERNAL_ERROR.to("order ${cmd.orderId}'s stock pre deduct failed", e)
        }

        log.info("order ${cmd.orderId}'s stock pre deduct success")
    }


}
