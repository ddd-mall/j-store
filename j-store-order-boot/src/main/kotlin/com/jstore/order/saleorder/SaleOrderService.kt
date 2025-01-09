package com.jstore.com.jstore.order.saleorder

import com.jstore.common.errors.CommonErrors
import com.jstore.order.acl.GoodsId
import com.jstore.order.risk.VerifySaleOrderCreateRiskCmd
import com.jstore.order.risk.VerifySaleOrderCreateRiskCmdHandler
import com.jstore.order.saleorder.SaleOrder
import com.jstore.order.saleorder.NormalSaleOrderCreateCMDHandler
import com.jstore.order.saleorder.NormalSaleOrderCreateCmd
import com.jstore.order.stock.StockPreDeductCmd
import com.jstore.order.stock.StockPreDeductHandler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class SaleOrderService(
    private val normalSaleOrderCreateCMDHandler: NormalSaleOrderCreateCMDHandler,
    private val verifySaleOrderCreateRiskCmdHandler: VerifySaleOrderCreateRiskCmdHandler,
    private val stockPreDeductHandler: StockPreDeductHandler
) {

    @Transactional(
        rollbackFor = [Exception::class],
        timeout = 5,
        isolation = Isolation.READ_COMMITTED,
        propagation = Propagation.REQUIRED
    )
    fun create(cmd: NormalSaleOrderCreateCmd): SaleOrder {
        verifySaleOrderCreateRiskCmdHandler.verify(VerifySaleOrderCreateRiskCmd())
        val saleOrder = normalSaleOrderCreateCMDHandler.create(cmd)
        val preDeductCmd = saleOrder.getId()?.let { id ->
            StockPreDeductCmd(id, saleOrder.orderItems.associate { GoodsId(it.spuId, it.skuId) to it.count })
        } ?: throw CommonErrors.ILLEGAL_STATE.to("sale order id is null")
        stockPreDeductHandler.handle(preDeductCmd)
        return saleOrder
    }
}