package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventBase
import com.jstore.common.framework.DomainEventId
import java.time.LocalDateTime

const val saleOrderTopic: String = "sale-order"

data class SaleOrderPrepareEvent(
    val createCMD: NormalSaleOrderCreateCmd
)

data class SaleOrderCreatedEvent(
    val order: SaleOrder,
    var id: DomainEventId? = null,
) : DomainEventBase(), DomainEvent {
    override fun topic(): String = saleOrderTopic
    override fun id(): DomainEventId? = id
}

data class FailToCreateSaleOrderEvent(
    val createCMD: NormalSaleOrderCreateCmd,
    val createTime: LocalDateTime,
    val id: DomainEventId? = null,
) : DomainEventBase(), DomainEvent {
    override fun id(): DomainEventId? = id
    override fun topic(): String = saleOrderTopic
}