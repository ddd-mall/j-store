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
import com.jstore.common.utils.Result
import com.jstore.payment.domain.payment.PaymentOrder
import com.jstore.payment.domain.payment.PaymentRefundId
import java.time.Instant

interface PaymentUseCase {
    fun createForOrder(request: PaymentOrderRequest): Result<PaymentOrder, BusinessError>

    fun getByOrderId(orderId: Long): Result<PaymentOrder, BusinessError>

    fun getByRefundId(refundId: PaymentRefundId): Result<PaymentOrder, BusinessError>

    fun capture(
        command: PaymentCaptureCommand,
        occurredAt: Instant = Instant.now(),
    ): Result<Boolean, BusinessError>

    fun requestRefund(
        request: PaymentRefundRequest,
        occurredAt: Instant = Instant.now(),
    ): Result<PaymentRefundId, BusinessError>

    fun retryRefund(
        refundId: PaymentRefundId,
        occurredAt: Instant = Instant.now(),
    ): Result<Boolean, BusinessError>

    fun markRefundSucceeded(
        refundId: PaymentRefundId,
        providerRefundId: String,
        occurredAt: Instant = Instant.now(),
    ): Result<Boolean, BusinessError>

    fun markRefundFailed(
        refundId: PaymentRefundId,
        reason: String,
        occurredAt: Instant = Instant.now(),
    ): Result<Boolean, BusinessError>
}
