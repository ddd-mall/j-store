package com.jstore.order.domain.risk

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.order.domain.saleorder.SaleOrderPrepareToCreateEvent
import org.springframework.context.ApplicationEvent
import org.springframework.core.ResolvableType
import org.springframework.stereotype.Component

@Component
class VerifyWhenSaleOrderPrepareToCreatePolicy(
    private val saleOrderCreateRiskVerifyCmdHandler: SaleOrderCreateRiskVerifyCmdHandler,
): DomainEventListener {

    override fun supportsAsyncExecution(): Boolean = false
    override fun supportsEventType(eventType: ResolvableType): Boolean {
        return eventType.type == SaleOrderPrepareToCreateEvent::class.java
    }

    override fun onDomainEvent(event: DomainEvent) {
        when(event) {
            is SaleOrderPrepareToCreateEvent -> {
                val riskVerifyCmd = SaleOrderCreateRiskVerifyCmd(
                    token = event.createCMD.token,
                    userInfo = event.createCMD.buyerUserInfo)
                saleOrderCreateRiskVerifyCmdHandler.verify(riskVerifyCmd)
            }
        }

    }


}