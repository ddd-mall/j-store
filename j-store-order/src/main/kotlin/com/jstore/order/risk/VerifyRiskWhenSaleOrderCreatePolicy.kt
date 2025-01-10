package com.jstore.order.risk

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventListener
import com.jstore.order.saleorder.SaleOrderCreatedEvent
import com.jstore.order.saleorder.saleOrderTopic
import org.springframework.stereotype.Component

@Component
class VerifyRiskWhenSaleOrderCreatePolicy(
  private val verifySaleOrderCreateRiskCmdHandler: VerifySaleOrderCreateRiskCmdHandler
) : DomainEventListener {
    override fun name(): String = this::class.qualifiedName!!
    override fun onTopics(): List<String> = listOf(saleOrderTopic)

    override fun async(): Boolean = false

    override fun handle(event: DomainEvent) {
        when (event) {
            is SaleOrderCreatedEvent -> {
                verifySaleOrderCreateRiskCmdHandler.verify(VerifySaleOrderCreateRiskCmd())
                event.mutex.notifyAll()
            }
        }
        event.markSuccess(this)
    }
}