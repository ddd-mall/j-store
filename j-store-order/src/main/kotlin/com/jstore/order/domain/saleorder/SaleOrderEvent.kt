package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventBase
import com.jstore.common.framework.DomainEventId
import java.time.LocalDateTime

const val saleOrderTopic: String = "sale-order"

data class SaleOrderCreatedEvent(
    val orderId: SaleOrderId,
    val createTime: LocalDateTime,
    val orderType: OrderType,
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