package com.jstore.order.stock

import com.jstore.common.errors.CommonErrors
import com.jstore.order.acl.GoodsId
import com.jstore.order.saleorder.SaleOrderId
import com.jstore.order.saleorder.SaleOrderRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

data class StockDeductCmd(
    val orderId: SaleOrderId,
    val goodsIds: List<GoodsId>
)

@Component
open class StockDeductCmdHandler(
    private val orderRepository: SaleOrderRepository,
    private val stockRepository: StockRepository,

    ) {
    @Transactional
    open fun handle(cmd: StockDeductCmd) {

        val order = orderRepository.findById(cmd.orderId)
        order ?: throw IllegalArgumentException("order ${cmd.orderId} not found")

        val orderStocks = stockRepository.findAllByOrderId(cmd.orderId)

        val notExist = cmd.goodsIds.filter { orderStocks.none { stock -> stock.goodsId == it } }
        if (notExist.isNotEmpty()) {
            throw CommonErrors.RESOURCE_NOT_FOUND.to("goods ${notExist}'s stock not found in order ${cmd.orderId}")
        }

        val needOp = orderStocks.filter { cmd.goodsIds.any { goodsId -> it.goodsId == goodsId } }
        try {
            needOp.forEach(Stock::deduct)
            stockRepository.saveBatch(needOp)
        } catch (e: Exception) {
            needOp.forEach(Stock::rollback)
            throw CommonErrors.INTERNAL_ERROR.to("stock deduct failed, order ${cmd.orderId}")
        }

    }

}