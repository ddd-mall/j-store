package com.jstore.order.domain.refund


import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.order.domain.saleorder.SaleOrderId
import java.time.LocalDateTime

data class RefundOrder(
    override val id: RefundOrderId?,
    val refundType: RefundType,
    val saleOrderId: SaleOrderId,
    val reason: String?,
    val refundAmount: Price,
    val createTime: LocalDateTime? = null,
    val updateTime: LocalDateTime? = null,
) : Entity<RefundOrderId>

data class RefundOrderId(override val value: Long) : Id<Long>(value)

enum class RefundType {
    SEVEN_DAY_NO_REASON_REFUND
}