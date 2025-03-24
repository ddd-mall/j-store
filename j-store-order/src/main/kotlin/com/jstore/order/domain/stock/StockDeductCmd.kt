package com.jstore.order.domain.stock

import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.order.acl.StockServiceACL
import com.jstore.order.domain.saleorder.SaleOrderId
import org.springframework.stereotype.Component

data class StockDeductCmd(
    val orderId: SaleOrderId,
)

@Component
open class StockDeductCmdHandler(
    private val stockServiceACL: StockServiceACL,
    private val stockRepository: StockRepository
) {
    private val log: Logger = LoggerFactory.getLogger(this::class)


    open fun handle(cmd: StockDeductCmd) {
        val orderStocks = stockRepository.findAllByOrderId(cmd.orderId)
        orderStocks.ifEmpty { throw CommonErrors.ILLEGAL_STATE.msg("order ${cmd.orderId}'s stock not exists") }

        try {
            orderStocks.forEach{
                stockServiceACL.confirm(it.outerStockId!!)
                it.deduct()
            }
            stockRepository.saveBatch(orderStocks)
            log.info("stock deduct success, order: ${cmd.orderId}")
        } catch (e: Exception) {
            orderStocks.forEach(Stock::rollback)
            stockRepository.saveBatch(orderStocks)
            log.error("stock deduct failed, order: ${cmd.orderId}")
            throw CommonErrors.INTERNAL_ERROR.msg("stock deduct failed, order ${cmd.orderId}")
        }

    }

}