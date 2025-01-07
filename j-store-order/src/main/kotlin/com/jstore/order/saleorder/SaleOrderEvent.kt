package com.jstore.order.saleorder

import com.jstore.common.framework.DomainEvent
import java.time.LocalDateTime

const val saleOrderTopic: String = "sale-order"

data class NormalSaleOrderCreatedEvent(
    val saleOrderId: SaleOrderId,
    val createTime: LocalDateTime
) : DomainEvent {
    override fun topic(): String {
        return saleOrderTopic
    }
}

data class FailToCreateSaleOrderEvent(
    val createCMD: NormalSaleOrderCreateCmd,
    val createTime: LocalDateTime,
    val cause: Throwable,
) : DomainEvent {
    override fun topic(): String {
        return saleOrderTopic
    }

}