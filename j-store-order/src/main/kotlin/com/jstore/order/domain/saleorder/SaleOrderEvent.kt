package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventId

const val saleOrderTopic: String = "sale-order"


data class SaleOrderPrepareToCreateEvent(
    val createCMD: NormalSaleOrderCreateCmd,
    override var id: DomainEventId? = null,
) : DomainEvent {
    override fun topic(): String = saleOrderTopic
}

data class SaleOrderCreatedEvent(
    val order: SaleOrder,
    override var id: DomainEventId? = null,
) : DomainEvent {
    override fun topic(): String = saleOrderTopic
}

data class FailToCreateSaleOrderEvent(
    val createCMD: NormalSaleOrderCreateCmd,
    override var id: DomainEventId? = null,
) : DomainEvent {

    override fun topic(): String = saleOrderTopic
}