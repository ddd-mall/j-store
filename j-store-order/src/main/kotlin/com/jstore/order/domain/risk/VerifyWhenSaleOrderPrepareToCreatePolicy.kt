package com.jstore.order.domain.risk

import com.jstore.common.framework.DomainEventListener
import com.jstore.order.domain.saleorder.SaleOrderPrepareToCreateEvent
import org.springframework.stereotype.Component

@Component
class VerifyWhenSaleOrderPrepareToCreatePolicy(
    private val saleOrderCreateRiskVerifyCmdHandler: SaleOrderCreateRiskVerifyCmdHandler,
): DomainEventListener<SaleOrderPrepareToCreateEvent> {

    override fun supportsAsyncExecution(): Boolean = false
    override fun onApplicationEvent(event: SaleOrderPrepareToCreateEvent) {
        val riskVerifyCmd = SaleOrderCreateRiskVerifyCmd(
            token = event.createCMD.token,
            userInfo = event.createCMD.buyerUserInfo)
        saleOrderCreateRiskVerifyCmdHandler.verify(riskVerifyCmd)
    }


}