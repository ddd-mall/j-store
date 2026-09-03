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
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.payment.domain.payment.PaymentErrors
import com.jstore.payment.domain.payment.PaymentOrder
import com.jstore.payment.domain.payment.PaymentOrderId
import com.jstore.payment.domain.payment.PaymentOrderImpl
import com.jstore.payment.domain.payment.PaymentRefundId
import com.jstore.shop.api.MerchantCapability
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MerchantPaymentServiceTest {
    private val payment =
        PaymentOrderImpl(
            PaymentOrderId(1),
            orderId = 9,
            merchantId = 7,
            payableAmount = Price.ofFen(100),
            currency = "JPY",
        )

    @Test
    fun `unauthorized payment is hidden as not found`() {
        val payments = StubPaymentUseCase(payment)
        val service = MerchantPaymentService(payments) { _, _, _ -> false }

        val result = service.get(accountId = 3, orderId = 9)

        assertEquals(PaymentErrors.ORDER_NOT_FOUND, assertIs<Failure<*>>(result).error)
    }

    @Test
    fun `authorized capture delegates only after manage authorization`() {
        val payments = StubPaymentUseCase(payment)
        val service =
            MerchantPaymentService(payments) { accountId, merchantId, capability ->
                accountId == 3L &&
                    merchantId == 7L &&
                    capability == MerchantCapability.PAYMENT_MANAGE
            }
        val command = PaymentCaptureCommand(9, "provider-1", Price.ofFen(100), "JPY")

        val result = service.capture(accountId = 3, command)

        assertEquals(true, assertIs<Success<Boolean>>(result).value)
        assertEquals(1, payments.captureCalls)
    }

    private class StubPaymentUseCase(private val payment: PaymentOrder) : PaymentUseCase {
        var captureCalls = 0
            private set

        override fun getByOrderId(orderId: Long): Result<PaymentOrder, BusinessError> =
            Success(payment)

        override fun capture(
            command: PaymentCaptureCommand,
            occurredAt: Instant,
        ): Result<Boolean, BusinessError> {
            captureCalls++
            return Success(true)
        }

        override fun createForOrder(
            request: PaymentOrderRequest
        ): Result<PaymentOrder, BusinessError> = unsupported()

        override fun getByRefundId(refundId: PaymentRefundId): Result<PaymentOrder, BusinessError> =
            unsupported()

        override fun requestRefund(
            request: PaymentRefundRequest,
            occurredAt: Instant,
        ): Result<PaymentRefundId, BusinessError> = unsupported()

        override fun retryRefund(
            refundId: PaymentRefundId,
            occurredAt: Instant,
        ): Result<Boolean, BusinessError> = unsupported()

        override fun markRefundSucceeded(
            refundId: PaymentRefundId,
            providerRefundId: String,
            occurredAt: Instant,
        ): Result<Boolean, BusinessError> = unsupported()

        override fun markRefundFailed(
            refundId: PaymentRefundId,
            reason: String,
            occurredAt: Instant,
        ): Result<Boolean, BusinessError> = unsupported()

        private fun <T> unsupported(): Result<T, BusinessError> =
            throw UnsupportedOperationException()
    }
}
