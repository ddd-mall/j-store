package com.jstore.payment.controller

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.fold
import com.jstore.payment.domain.payment.PaymentErrors
import com.jstore.payment.domain.payment.PaymentOrder
import com.jstore.payment.domain.payment.PaymentRefundId
import com.jstore.payment.service.PaymentUseCase
import com.jstore.payment.service.PaymentCaptureCommand
import com.jstore.shop.domain.merchant.MerchantId
import com.jstore.shop.domain.merchant.MerchantPermission
import com.jstore.shop.service.MerchantAuthorizationService
import com.jstore.user.domain.useraccount.UserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments")
@RequireLogin
class PaymentController(
    private val service: PaymentUseCase,
    private val merchantAuthorization: MerchantAuthorizationService,
) {
    data class CaptureRequest(
        val providerTransactionId: String,
        val amount: Long,
        val currency: String = "CNY",
    )

    data class RefundResultRequest(
        val providerRefundId: String? = null,
        val failureReason: String? = null,
    )

    data class ErrorResponse(val message: String, val errorCode: String)

    data class Response(
        val id: Long,
        val orderId: Long,
        val merchantId: Long,
        val payableAmount: Long,
        val currency: String,
        val status: String,
        val providerTransactionId: String?,
        val refunds: List<RefundResponse>,
    )

    data class RefundResponse(
        val id: Long,
        val afterSaleId: Long,
        val amount: Long,
        val status: String,
        val failureReason: String?,
    )

    @GetMapping("/orders/{orderId}")
    fun get(@CurrentUserId userId: UserId, @PathVariable orderId: Long): ResponseEntity<*> =
        authorized(userId, orderId, MerchantPermission.PAYMENT_READ).response { it.toResponse() }

    /** 预上线阶段的渠道回调模拟入口；接真实支付渠道时应替换为签名验签适配器。 */
    @PostMapping("/orders/{orderId}/capture")
    fun capture(
        @CurrentUserId userId: UserId,
        @PathVariable orderId: Long,
        @RequestBody body: CaptureRequest,
    ): ResponseEntity<*> {
        val authorization = authorized(userId, orderId, MerchantPermission.PAYMENT_MANAGE)
        if (authorization is Failure) return authorization.response {}
        return service
            .capture(
                PaymentCaptureCommand(
                    orderId,
                    body.providerTransactionId,
                    Price.ofFen(body.amount),
                    body.currency,
                )
            )
            .response { mapOf("changed" to it) }
    }

    /** 预上线阶段的退款渠道结果模拟入口。 */
    @PostMapping("/refunds/{refundId}/result")
    fun refundResult(
        @CurrentUserId userId: UserId,
        @PathVariable refundId: Long,
        @RequestBody body: RefundResultRequest,
    ): ResponseEntity<*> {
        val id = PaymentRefundId(refundId)
        val authorization = authorizedRefund(userId, id, MerchantPermission.PAYMENT_MANAGE)
        if (authorization is Failure) return authorization.response {}
        val result =
            if (!body.providerRefundId.isNullOrBlank()) {
                service.markRefundSucceeded(id, body.providerRefundId)
            } else {
                service.markRefundFailed(id, body.failureReason.orEmpty())
            }
        return result.response { mapOf("changed" to it) }
    }

    private fun authorized(
        userId: UserId,
        orderId: Long,
        permission: MerchantPermission,
    ): Result<PaymentOrder, BusinessError> {
        val result = service.getByOrderId(orderId)
        return result.fold(
            onSuccess = {
                if (
                    merchantAuthorization.hasPermission(
                        userId.value,
                        MerchantId(it.merchantId),
                        permission,
                    )
                )
                    result
                else Failure(PaymentErrors.ORDER_NOT_FOUND)
            },
            onFailure = { result },
        )
    }

    private fun authorizedRefund(
        userId: UserId,
        refundId: PaymentRefundId,
        permission: MerchantPermission,
    ): Result<PaymentOrder, BusinessError> {
        val result = service.getByRefundId(refundId)
        return result.fold(
            onSuccess = {
                if (
                    merchantAuthorization.hasPermission(
                        userId.value,
                        MerchantId(it.merchantId),
                        permission,
                    )
                )
                    result
                else Failure(PaymentErrors.REFUND_NOT_FOUND)
            },
            onFailure = { result },
        )
    }

    private fun PaymentOrder.toResponse() =
        Response(
            id.value,
            orderId,
            merchantId,
            payableAmount.fen,
            currency,
            status.name,
            capture?.providerTransactionId,
            refunds.map {
                RefundResponse(
                    it.id.value,
                    it.afterSaleId,
                    it.amount.fen,
                    it.status.name,
                    it.failureReason,
                )
            },
        )

    private fun <T> Result<T, BusinessError>.response(mapper: (T) -> Any): ResponseEntity<*> =
        fold(
            onSuccess = { ResponseEntity.ok(mapper(it)) },
            onFailure = {
                ResponseEntity.status(it.httpCode).body(ErrorResponse(it.message, it.errorCode))
            },
        )
}
