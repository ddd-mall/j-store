package com.jstore.order.service

import com.jstore.common.errors.CommonErrors
import com.jstore.order.acl.GoodsId
import com.jstore.order.domain.risk.SaleOrderCreateRiskVerifyCmd
import com.jstore.order.domain.risk.SaleOrderCreateRiskVerifyCmdHandler
import com.jstore.order.domain.saleorder.NormalSaleOrderCreateCMDHandler
import com.jstore.order.domain.saleorder.NormalSaleOrderCreateCmd
import com.jstore.order.domain.saleorder.SaleOrder
import com.jstore.order.domain.stock.StockPreDeductCmd
import com.jstore.order.domain.stock.StockPreDeductHandler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
open class SaleOrderService(
    private val normalSaleOrderCreateCMDHandler: NormalSaleOrderCreateCMDHandler,
    private val saleOrderCreateRiskVerifyCmdHandler: SaleOrderCreateRiskVerifyCmdHandler,
    private val stockPreDeductHandler: StockPreDeductHandler
) {

    @Transactional(
        rollbackFor = [Exception::class],
        timeout = 5,
        isolation = Isolation.READ_COMMITTED,
        propagation = Propagation.REQUIRED
    )
    open fun create(cmd: NormalSaleOrderCreateCmd): SaleOrder {
        val riskVerifyCmd = SaleOrderCreateRiskVerifyCmd(
            token = cmd.token,
            uid = cmd.buyerUserInfo.uid
        )

        saleOrderCreateRiskVerifyCmdHandler.verify(riskVerifyCmd)
        val saleOrder = normalSaleOrderCreateCMDHandler.create(cmd)
        val preDeductCmd = saleOrder.id()?.let { id ->
            StockPreDeductCmd(
                orderId = id,
                goodsIdsQuantityMap = saleOrder.orderItems.associate { GoodsId(it.spuId, it.skuId) to it.count })
        } ?: throw CommonErrors.ILLEGAL_STATE.to("sale order id is null")
        stockPreDeductHandler.handle(preDeductCmd)
        return saleOrder
    }
}