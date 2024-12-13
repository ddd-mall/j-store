package com.jstore.com.jstore.order.refund

import com.jstore.com.jstore.framework.Entity
import com.jstore.common.errors.CommonErrors
import com.jstore.order.common.Id
import com.jstore.order.saleorder.SaleOrderId
import com.jstore.common.properties.Price
import java.time.LocalDateTime

data class RefundOrder(
    private val id: RefundOrderId?,
    val refundType: RefundType,
    val saleOrderId: SaleOrderId,
    val reason: String?,
    val refundAmount: Price,
    val createTime: LocalDateTime? = null,
    val updateTime: LocalDateTime? = null,
): Entity<RefundOrderId> {
    override fun getId(): RefundOrderId {
        return id?:throw CommonErrors.ILLEGAL_STATE.withMsg("退款单尚未创建完成")
    }
}

data class RefundOrderId(override val value: Long): Id<Long>(value)

enum class RefundType {
    SEVEN_DAY_NO_REASON_REFUND
}