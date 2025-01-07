package com.jstore.order.stock

import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.order.acl.GoodsId
import com.jstore.order.saleorder.SaleOrderRepository
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
    private val saleOrderRepository: SaleOrderRepository,
    private val stockRepository: StockRepository,
    private val stockFactory: StockFactory
) {
    private val log: Logger = LoggerFactory.getLogger(this::class)


    @Transactional(rollbackOn = [Exception::class], value = Transactional.TxType.REQUIRED)
    open fun handle(cmd: StockPreDeductCmd): StockOperationResponse {
        saleOrderRepository.findById(cmd.orderId) ?: let {
            throw CommonErrors.RESOURCE_NOT_FOUND.to("order ${cmd.orderId} not found")
        }
        val orderStocks = stockRepository.findAllByOrderId(cmd.orderId)

        val waitToCreate = cmd.goodsIdsQuantityMap.filter { goodsIdQuantityEntry ->
            orderStocks.none { it.goodsId == goodsIdQuantityEntry.key }
        }.toMap().let {
            StockPreDeductCmd(
                orderId = cmd.orderId,
                goodsIdsQuantityMap = it
            )
        }
        val createdStock = stockFactory.create(waitToCreate)


        try {
            orderStocks.forEach(Stock::preDeduct)
            createdStock.forEach(Stock::preDeduct)
            orderStocks.let(stockRepository::saveBatch)
            createdStock.let(stockRepository::saveBatch)
        } catch (e: Exception) {
            orderStocks.forEach(Stock::rollback)
            createdStock.forEach(Stock::rollback)
            throw CommonErrors.INTERNAL_ERROR.to("order ${cmd.orderId}'s stock pre deduct failed", e)
        }


        return StockOperationResponse("mock opId", true, "success")
    }


}
