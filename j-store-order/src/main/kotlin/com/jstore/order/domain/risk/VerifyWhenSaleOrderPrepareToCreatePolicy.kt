package com.jstore.order.domain.risk

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventListener
import com.jstore.common.framework.DomainEventRegistry
import com.jstore.order.domain.saleorder.SaleOrderPrepareToCreateEvent
import com.jstore.order.domain.saleorder.saleOrderTopic
import org.springframework.stereotype.Component

@Component
class VerifyWhenSaleOrderPrepareToCreatePolicy(
    private val saleOrderCreateRiskVerifyCmdHandler: SaleOrderCreateRiskVerifyCmdHandler,
    domainEventRegistry: DomainEventRegistry
): DomainEventListener {
    init {
        domainEventRegistry.register(this)
    }
    override fun name(): String = this::class.simpleName!!
    override fun onTopics(): List<String> = listOf(saleOrderTopic)
    override fun async(): Boolean = false

    override fun handle(event: DomainEvent) {
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