package com.jstore.order.saleorder

import com.jstore.common.framework.DomainEventBase
import com.jstore.common.framework.DomainEventId
import java.time.LocalDateTime

const val saleOrderTopic: String = "sale-order"

data class SaleOrderCreatedEvent(
    val orderId: SaleOrderId,
    val createTime: LocalDateTime,
    val orderType: OrderType,
    override var id: DomainEventId? = null,
) : DomainEventBase(id) {
    override fun topic(): String = saleOrderTopic
}

data class FailToCreateSaleOrderEvent(
    val createCMD: NormalSaleOrderCreateCmd,
    val createTime: LocalDateTime,
    override val id: DomainEventId? = null,
) : DomainEventBase(id) {
    override fun topic(): String = saleOrderTopic
}