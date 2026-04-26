package com.jstore.order.domain.order.command

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId

/**
 * 拒绝退款命令
 */
data class OrderRejectRefundCMD(
    val orderId: OrderId,
    val rejectReason: String,
    val itemIds: List<OrderItemId>,
) {
    fun validate(): Result<OrderRejectRefundCMD, BusinessError> {
        if (rejectReason.isBlank()) return Failure(OrderErrors.REJECT_REASON_INVALID)
        if (itemIds.isEmpty()) return Failure(OrderErrors.REFUND_ITEMS_EMPTY)
        return Success(this)
    }
}
