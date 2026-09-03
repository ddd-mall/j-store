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
package com.jstore.payment.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.payment.domain.payment.PaymentErrors
import com.jstore.payment.domain.payment.PaymentOrder
import com.jstore.payment.domain.payment.PaymentRefundId
import com.jstore.shop.api.MerchantAuthorizationQuery
import com.jstore.shop.api.MerchantCapability

interface MerchantPaymentUseCase {
    fun get(accountId: Long, orderId: Long): Result<PaymentOrder, BusinessError>

    fun capture(accountId: Long, command: PaymentCaptureCommand): Result<Boolean, BusinessError>

    fun recordRefundResult(
        accountId: Long,
        refundId: PaymentRefundId,
        providerRefundId: String?,
        failureReason: String?,
    ): Result<Boolean, BusinessError>
}

class MerchantPaymentService(
    private val payments: PaymentUseCase,
    private val authorization: MerchantAuthorizationQuery,
) : MerchantPaymentUseCase {
    override fun get(accountId: Long, orderId: Long): Result<PaymentOrder, BusinessError> {
        val payment =
            when (val result = payments.getByOrderId(orderId)) {
                is Success -> result.value
                is Failure -> return result
            }
        return if (allowed(accountId, payment, MerchantCapability.PAYMENT_READ)) Success(payment)
        else Failure(PaymentErrors.ORDER_NOT_FOUND)
    }

    override fun capture(
        accountId: Long,
        command: PaymentCaptureCommand,
    ): Result<Boolean, BusinessError> {
        val payment =
            when (val result = payments.getByOrderId(command.orderId)) {
                is Success -> result.value
                is Failure -> return result
            }
        if (!allowed(accountId, payment, MerchantCapability.PAYMENT_MANAGE))
            return Failure(PaymentErrors.ORDER_NOT_FOUND)
        return payments.capture(command)
    }

    override fun recordRefundResult(
        accountId: Long,
        refundId: PaymentRefundId,
        providerRefundId: String?,
        failureReason: String?,
    ): Result<Boolean, BusinessError> {
        val payment =
            when (val result = payments.getByRefundId(refundId)) {
                is Success -> result.value
                is Failure -> return result
            }
        if (!allowed(accountId, payment, MerchantCapability.PAYMENT_MANAGE))
            return Failure(PaymentErrors.REFUND_NOT_FOUND)
        return if (!providerRefundId.isNullOrBlank())
            payments.markRefundSucceeded(refundId, providerRefundId)
        else payments.markRefundFailed(refundId, failureReason.orEmpty())
    }

    private fun allowed(accountId: Long, payment: PaymentOrder, capability: MerchantCapability) =
        authorization.isAllowed(accountId, payment.merchantId, capability)
}
