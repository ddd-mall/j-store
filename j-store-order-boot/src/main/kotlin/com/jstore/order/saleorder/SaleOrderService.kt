package com.jstore.com.jstore.order.saleorder

import com.jstore.order.risk.VerifySaleOrderCreateRiskCmd
import com.jstore.order.risk.VerifySaleOrderCreateRiskCmdHandler
import com.jstore.order.saleorder.SaleOrder
import com.jstore.order.saleorder.NormalSaleOrderCreateCMDHandler
import com.jstore.order.saleorder.NormalSaleOrderCreateCmd
import org.springframework.stereotype.Service

@Service
class SaleOrderService(
    private val normalSaleOrderCreateCMDHandler: NormalSaleOrderCreateCMDHandler,
    private val verifySaleOrderCreateRiskCmdHandler: VerifySaleOrderCreateRiskCmdHandler
) {

    fun create(cmd: NormalSaleOrderCreateCmd): SaleOrder {
        verifySaleOrderCreateRiskCmdHandler.verify(VerifySaleOrderCreateRiskCmd())
        return normalSaleOrderCreateCMDHandler.create(cmd)
    }
}