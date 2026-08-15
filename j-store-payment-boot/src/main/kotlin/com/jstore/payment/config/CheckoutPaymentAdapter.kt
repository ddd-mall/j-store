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

import com.jstore.payment.domain.payment.TradePaymentId
import com.jstore.payment.domain.payment.TradePaymentRepository
import com.jstore.payment.domain.payment.TradePaymentStatus
import com.jstore.trade.service.CheckoutPaymentGateway
import com.jstore.trade.service.CheckoutPaymentView
import java.time.Instant

class CheckoutPaymentAdapter(
    private val payments: TradePaymentRepository,
    private val now: () -> Instant = Instant::now,
) : CheckoutPaymentGateway {
    override fun findReadyPayment(paymentId: Long): CheckoutPaymentView? {
        val payment = payments.findById(TradePaymentId(paymentId)) ?: return null
        if (payment.status != TradePaymentStatus.READY) return null
        val expiresAt = requireNotNull(payment.expiresAt)
        if (expiresAt <= now()) return null
        return CheckoutPaymentView(
            payment.id.value,
            payment.status.name,
            payment.payableAmount.fen,
            payment.currency,
            requireNotNull(payment.payAction),
            expiresAt,
        )
    }
}
