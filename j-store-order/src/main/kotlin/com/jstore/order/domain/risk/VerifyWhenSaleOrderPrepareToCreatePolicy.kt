package com.jstore.order.domain.risk

import com.jstore.common.framework.DomainEventListener
import com.jstore.common.framework.DomainEventRegistry
import com.jstore.order.domain.saleorder.SaleOrderPrepareToCreateEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
class VerifyWhenSaleOrderPrepareToCreatePolicy(
    private val saleOrderCreateRiskVerifyCmdHandler: SaleOrderCreateRiskVerifyCmdHandler,
    domainEventRegistry: DomainEventRegistry
): DomainEventListener<SaleOrderPrepareToCreateEvent>, ApplicationListener<SaleOrderPrepareToCreateEvent> {
    init {
        domainEventRegistry.register(this)
    }
    override fun supportsAsyncExecution(): Boolean = false
    override fun onApplicationEvent(event: SaleOrderPrepareToCreateEvent) {
        val riskVerifyCmd = SaleOrderCreateRiskVerifyCmd(
            token = event.createCMD.token,
            userInfo = event.createCMD.buyerUserInfo)
        saleOrderCreateRiskVerifyCmdHandler.verify(riskVerifyCmd)
    }


}