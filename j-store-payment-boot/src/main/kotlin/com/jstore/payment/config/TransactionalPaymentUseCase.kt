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
package com.jstore.payment.config

import com.jstore.payment.domain.payment.PaymentRefundId
import com.jstore.payment.service.PaymentCaptureCommand
import com.jstore.payment.service.PaymentOrderRequest
import com.jstore.payment.service.PaymentRefundRequest
import com.jstore.payment.service.PaymentUseCase
import java.time.Instant
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class TransactionalPaymentUseCase(
    private val delegate: PaymentUseCase,
    transactionManager: PlatformTransactionManager,
) : PaymentUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun createForOrder(request: PaymentOrderRequest) = tx {
        delegate.createForOrder(request)
    }

    override fun getByOrderId(orderId: Long) = query { delegate.getByOrderId(orderId) }

    override fun getByRefundId(refundId: PaymentRefundId) = query {
        delegate.getByRefundId(refundId)
    }

    override fun capture(command: PaymentCaptureCommand, occurredAt: Instant) = tx {
        delegate.capture(command, occurredAt)
    }

    override fun requestRefund(request: PaymentRefundRequest, occurredAt: Instant) = tx {
        delegate.requestRefund(request, occurredAt)
    }

    override fun retryRefund(refundId: PaymentRefundId, occurredAt: Instant) = tx {
        delegate.retryRefund(refundId, occurredAt)
    }

    override fun markRefundSucceeded(
        refundId: PaymentRefundId,
        providerRefundId: String,
        occurredAt: Instant,
    ) = tx { delegate.markRefundSucceeded(refundId, providerRefundId, occurredAt) }

    override fun markRefundFailed(refundId: PaymentRefundId, reason: String, occurredAt: Instant) =
        tx {
            delegate.markRefundFailed(refundId, reason, occurredAt)
        }

    private fun <T> tx(block: () -> T): T = requireNotNull(write.execute { block() })

    private fun <T> query(block: () -> T): T = requireNotNull(read.execute { block() })
}
