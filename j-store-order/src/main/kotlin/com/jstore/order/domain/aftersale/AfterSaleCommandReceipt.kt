package com.jstore.order.domain.aftersale

import com.jstore.common.properties.Price
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import java.time.LocalDateTime

enum class AfterSaleCommandType {
    CREATE,
    APPROVE,
    REJECT,
    CANCEL,
}

enum class AllocationAction {
    APPROVE,
    RELEASE,
}

data class RefundCapacityCeiling(
    val orderId: OrderId,
    val orderItemId: OrderItemId,
    val quantity: Int,
    val amount: Price,
)

data class AfterSaleCommandReceipt(
    val actorId: Long,
    val type: AfterSaleCommandType,
    val key: String,
    val requestHash: String,
    val afterSaleId: AfterSaleId,
    val resultStatus: AfterSaleStatus,
    val createdAt: LocalDateTime,
)
