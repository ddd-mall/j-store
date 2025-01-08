package com.jstore.order.stock

import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.order.acl.GoodsId
import com.jstore.order.saleorder.SaleOrderId
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component
import java.math.BigDecimal

class StockPreDeductCmd(
    val orderId: SaleOrderId,
    val goodsIdsQuantityMap: Map<GoodsId, BigDecimal>,
)

@Component
open class StockPreDeductHandler(
    private val stockRepository: StockRepository,
    private val stockFactory: StockFactory
) {
    private val log: Logger = LoggerFactory.getLogger(this::class)


    @Transactional(rollbackOn = [Exception::class], value = Transactional.TxType.REQUIRED)
    open fun handle(cmd: StockPreDeductCmd) {

        val stockList = stockRepository.findAllByOrderId(cmd.orderId).let {
            if (it.any { stock -> cmd.goodsIdsQuantityMap.keys.contains(stock.goodsId) }) {
                throw CommonErrors.ILLEGAL_STATE.to("order ${cmd.orderId}'s stock already exists")
            }
            it.ifEmpty {
                stockFactory.create(cmd)
            }
        }


        try {
            stockList.forEach(Stock::preDeduct)
            stockList.let(stockRepository::saveBatch)
        } catch (e: Exception) {
            stockList.forEach(Stock::rollback)
            throw CommonErrors.INTERNAL_ERROR.to("order ${cmd.orderId}'s stock pre deduct failed", e)
        }
    }


}
