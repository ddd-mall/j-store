package com.jstore.order.domain.order.command

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.CancellationCategory
import com.jstore.order.domain.order.CancellationReason
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderId

/** 取消订单命令 */
data class OrderCancelCMD(
    val orderId: OrderId,
    val category: CancellationCategory,
    val description: String,
) {
    fun validate(): Result<OrderCancelCMD, BusinessError> {
        if (description.isBlank()) return Failure(OrderErrors.CANCEL_REASON_INVALID)
        return Success(this)
    }

    fun toReason(): CancellationReason = CancellationReason(category, description)
}
