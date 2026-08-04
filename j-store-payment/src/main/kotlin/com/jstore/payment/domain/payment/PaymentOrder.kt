package com.jstore.payment.domain.payment

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AgreeGate
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.common.utils.Result
import java.time.Instant

data class PaymentOrderId(override val value: Long) : Id<Long>(value)
data class PaymentRefundId(override val value: Long) : Id<Long>(value)

enum class PaymentOrderStatus { PENDING, CAPTURED, PARTIALLY_REFUNDED, REFUNDED }
enum class PaymentRefundStatus { PENDING, SUCCEEDED, FAILED }

data class PaymentCapture(
    val providerTransactionId: String,
    val amount: Price,
    val capturedAt: Instant,
)

data class PaymentRefundItem(
    val orderItemId: Long,
    val skuId: Long,
    val quantity: Int,
    val amount: Price,
) {
    init {
        require(orderItemId > 0 && skuId > 0 && quantity > 0 && amount > Price.ZERO)
    }
}

data class PaymentRefund(
    val id: PaymentRefundId,
    val afterSaleId: Long,
    val items: List<PaymentRefundItem>,
    val amount: Price,
    var status: PaymentRefundStatus = PaymentRefundStatus.PENDING,
    var providerRefundId: String? = null,
    var failureReason: String? = null,
    val requestedAt: Instant,
    var completedAt: Instant? = null,
) {
    init {
        require(afterSaleId > 0 && items.isNotEmpty())
        require(amount == Price.sumOf(items.map { it.amount }))
    }
}

interface PaymentOrder : AgreeGate<PaymentOrderId> {
    val orderId: Long
    val merchantId: Long
    val payableAmount: Price
    val currency: String
    val status: PaymentOrderStatus
    val capture: PaymentCapture?
    val refunds: List<PaymentRefund>

    fun capture(providerTransactionId: String, amount: Price, currency: String, occurredAt: Instant): Result<Boolean, BusinessError>
    fun requestRefund(refund: PaymentRefund, occurredAt: Instant): Result<Boolean, BusinessError>
    fun retryRefund(refundId: PaymentRefundId, occurredAt: Instant): Result<Boolean, BusinessError>
    fun markRefundSucceeded(refundId: PaymentRefundId, providerRefundId: String, occurredAt: Instant): Result<Boolean, BusinessError>
    fun markRefundFailed(refundId: PaymentRefundId, reason: String, occurredAt: Instant): Result<Boolean, BusinessError>
}
