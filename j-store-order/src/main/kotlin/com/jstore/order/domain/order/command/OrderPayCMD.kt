package com.jstore.order.domain.order.command

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderId

/**
 * 支付订单命令
 */
data class OrderPayCMD(
    val orderId: OrderId,
    val paidAmount: Price,
) {
    fun validate(): Result<OrderPayCMD, BusinessError> {
        if (paidAmount <= Price.ZERO) return Failure(OrderErrors.PAY_AMOUNT_INVALID)
        return Success(this)
    }
}
