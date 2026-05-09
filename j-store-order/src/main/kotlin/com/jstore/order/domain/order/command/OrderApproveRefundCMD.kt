package com.jstore.order.domain.order.command

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId

/**
 * 批准退款命令
 */
data class OrderApproveRefundCMD(
    val orderId: OrderId,
    val itemIds: List<OrderItemId>,
) {
    fun validate(): Result<OrderApproveRefundCMD, BusinessError> {
        if (itemIds.isEmpty()) return Failure(OrderErrors.REFUND_ITEMS_EMPTY)
        return Success(this)
    }
}
