/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.payment.controller

import com.jstore.authentication.annotation.CurrentPrincipal
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.common.currency.SiteCurrencyPolicy
import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.fold
import com.jstore.payment.domain.payment.PaymentErrors
import com.jstore.payment.domain.payment.PaymentOrder
import com.jstore.payment.domain.payment.PaymentRefundId
import com.jstore.payment.service.PaymentCaptureCommand
import com.jstore.payment.service.PaymentUseCase
import com.jstore.shop.domain.merchant.MerchantId
import com.jstore.shop.domain.merchant.MerchantPermission
import com.jstore.shop.service.MerchantAuthorizationService
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
    private val currencyPolicy: SiteCurrencyPolicy,
) {
    data class CaptureRequest(
        val providerTransactionId: String,
        val amount: Long,
        val currency: String? = null,
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
    fun get(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable orderId: Long,
    ): ResponseEntity<*> =
        authorized(principal, orderId, MerchantPermission.PAYMENT_READ).response { it.toResponse() }

    /** 预上线阶段的渠道回调模拟入口；接真实支付渠道时应替换为签名验签适配器。 */
    @PostMapping("/orders/{orderId}/capture")
    fun capture(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable orderId: Long,
        @RequestBody body: CaptureRequest,
    ): ResponseEntity<*> {
        val authorization = authorized(principal, orderId, MerchantPermission.PAYMENT_MANAGE)
        if (authorization is Failure) return authorization.response {}
        val currency =
            currencyPolicy.select(body.currency)
                ?: return CommonBusinessError.INVALID_PARAM.msg("币种不属于当前站允许范围").errorResponse()
        return service
            .capture(
                PaymentCaptureCommand(
                    orderId,
                    body.providerTransactionId,
                    Price.ofFen(body.amount),
                    currency,
                )
            )
            .response { mapOf("changed" to it) }
    }

    /** 预上线阶段的退款渠道结果模拟入口。 */
    @PostMapping("/refunds/{refundId}/result")
    fun refundResult(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable refundId: Long,
        @RequestBody body: RefundResultRequest,
    ): ResponseEntity<*> {
        val id = PaymentRefundId(refundId)
        val authorization = authorizedRefund(principal, id, MerchantPermission.PAYMENT_MANAGE)
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
        principal: AuthenticatedPrincipal,
        orderId: Long,
        permission: MerchantPermission,
    ): Result<PaymentOrder, BusinessError> {
        val result = service.getByOrderId(orderId)
        return result.fold(
            onSuccess = {
                if (
                    merchantAuthorization.hasPermission(
                        principal.accountId.value,
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
        principal: AuthenticatedPrincipal,
        refundId: PaymentRefundId,
        permission: MerchantPermission,
    ): Result<PaymentOrder, BusinessError> {
        val result = service.getByRefundId(refundId)
        return result.fold(
            onSuccess = {
                if (
                    merchantAuthorization.hasPermission(
                        principal.accountId.value,
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

    private fun BusinessError.errorResponse(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(httpCode).body(ErrorResponse(message, errorCode))
}
