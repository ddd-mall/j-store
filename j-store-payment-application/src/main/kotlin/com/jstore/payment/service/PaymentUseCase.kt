package com.jstore.payment.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.payment.domain.payment.PaymentOrder
import com.jstore.payment.domain.payment.PaymentRefundId
import java.time.Instant

interface PaymentUseCase {
    fun createForOrder(request: PaymentOrderRequest): Result<PaymentOrder, BusinessError>
    fun getByOrderId(orderId: Long): Result<PaymentOrder, BusinessError>
    fun getByRefundId(refundId: PaymentRefundId): Result<PaymentOrder, BusinessError>
    fun capture(command: PaymentCaptureCommand, occurredAt: Instant = Instant.now()): Result<Boolean, BusinessError>
    fun requestRefund(request: PaymentRefundRequest, occurredAt: Instant = Instant.now()): Result<PaymentRefundId, BusinessError>
    fun retryRefund(refundId: PaymentRefundId, occurredAt: Instant = Instant.now()): Result<Boolean, BusinessError>
    fun markRefundSucceeded(refundId: PaymentRefundId, providerRefundId: String, occurredAt: Instant = Instant.now()): Result<Boolean, BusinessError>
    fun markRefundFailed(refundId: PaymentRefundId, reason: String, occurredAt: Instant = Instant.now()): Result<Boolean, BusinessError>
}
