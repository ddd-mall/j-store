package com.jstore.payment.domain.payment

import com.jstore.common.errors.BusinessError

object PaymentErrors {
    val ORDER_NOT_FOUND = BusinessError("支付单不存在", "Payment.Order.NotFound", 404)
    val ORDER_CONFLICT = BusinessError("订单支付快照冲突", "Payment.Order.Conflict", 409)
    val NOT_FOUND = BusinessError("支付单不存在", "Payment.NotFound", 404)
    val INVALID_STATE = BusinessError("支付单状态不允许当前操作", "Payment.State.Invalid", 409)
    val CAPTURE_INVALID = BusinessError("支付捕获信息无效", "Payment.Capture.Invalid", 409)
    val CAPTURE_CONFLICT = BusinessError("支付单已存在其他捕获流水", "Payment.Capture.Conflict", 409)
    val REFUND_INVALID = BusinessError("退款信息无效", "Payment.Refund.Invalid", 409)
    val REFUND_NOT_FOUND = BusinessError("退款单不存在", "Payment.Refund.NotFound", 404)
    val REFUND_PROVIDER_CONFLICT = BusinessError("退款单已关联其他渠道流水", "Payment.Refund.ProviderConflict", 409)
}
