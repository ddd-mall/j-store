package com.jstore.order.domain.order.command

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import com.jstore.order.domain.order.RefundCategory
import com.jstore.order.domain.order.RefundReason

/**
 * 申请退款命令
 */
data class OrderRequestRefundCMD(
    val orderId: OrderId,
    val category: RefundCategory,
    val description: String,
    val itemIds: List<OrderItemId>,
) {
    fun validate(): Result<OrderRequestRefundCMD, BusinessError> {
        if (description.isBlank()) return Failure(OrderErrors.REFUND_REASON_INVALID)
        if (itemIds.isEmpty()) return Failure(OrderErrors.REFUND_ITEMS_EMPTY)
        return Success(this)
    }

    fun toReason(): RefundReason = RefundReason(category, description)
}
