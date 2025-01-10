package com.jstore.order.stock

import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.order.saleorder.SaleOrderId
import org.springframework.stereotype.Component

data class StockDeductCmd(
    val orderId: SaleOrderId,
)

@Component
open class StockDeductCmdHandler(
    private val stockRepository: StockRepository
) {
    private val log: Logger = LoggerFactory.getLogger(this::class)


    open fun handle(cmd: StockDeductCmd) {
        val orderStocks = stockRepository.findAllByOrderId(cmd.orderId)
        orderStocks.ifEmpty { throw CommonErrors.ILLEGAL_STATE.to("order ${cmd.orderId}'s stock not exists") }

        try {
            orderStocks.forEach(Stock::deduct)
            stockRepository.saveBatch(orderStocks)
            log.info("stock deduct success, order: ${cmd.orderId}")
        } catch (e: Exception) {
            orderStocks.forEach(Stock::rollback)
            stockRepository.saveBatch(orderStocks)
            log.error("stock deduct failed, order: ${cmd.orderId}")
            throw CommonErrors.INTERNAL_ERROR.to("stock deduct failed, order ${cmd.orderId}")
        }

    }

}