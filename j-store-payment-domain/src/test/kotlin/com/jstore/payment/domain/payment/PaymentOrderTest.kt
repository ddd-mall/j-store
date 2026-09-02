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
package com.jstore.payment.domain.payment

import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.payment.domain.payment.event.PaymentCapturedEvent
import com.jstore.payment.domain.payment.event.PaymentRefundSucceededEvent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class PaymentOrderTest :
    FunSpec({
        fun payment() = PaymentOrderImpl(PaymentOrderId(1), 10, 20, Price.ofFen(1_000), "CNY")

        test("capture requires the frozen full amount and is idempotent by provider transaction") {
            val payment = payment()
            (payment.capture("txn-1", Price.ofFen(900), "CNY", Instant.EPOCH) is Failure) shouldBe
                true
            payment.status shouldBe PaymentOrderStatus.PENDING

            (payment.capture("txn-1", Price.ofFen(1_000), "CNY", Instant.EPOCH) as Success)
                .value shouldBe true
            payment.status shouldBe PaymentOrderStatus.CAPTURED
            payment.pendingDomainEvents().single()::class shouldBe PaymentCapturedEvent::class
            (payment.capture("txn-1", Price.ofFen(1_000), "CNY", Instant.EPOCH) as Success)
                .value shouldBe false
        }

        test("payment accepts real ISO currencies and rejects invented codes") {
            listOf("CNY", "JPY", "USD").forEach { currency ->
                PaymentOrderImpl(PaymentOrderId(1), 10, 20, Price.ofFen(1_000), currency)
                    .currency shouldBe currency
            }
            shouldThrow<IllegalArgumentException> {
                PaymentOrderImpl(PaymentOrderId(1), 10, 20, Price.ofFen(1_000), "ZZZ")
            }
        }

        test("refund success is separate from refund request and updates payment status") {
            val payment = payment()
            payment.capture("txn-1", Price.ofFen(1_000), "CNY", Instant.EPOCH)
            payment.acknowledgeDomainEvents(
                payment.pendingDomainEvents().mapTo(linkedSetOf()) { it.eventId }
            )
            val refund =
                PaymentRefund(
                    PaymentRefundId(2),
                    30,
                    listOf(PaymentRefundItem(40, 50, 1, Price.ofFen(400))),
                    Price.ofFen(400),
                    requestedAt = Instant.EPOCH,
                )

            payment.requestRefund(refund, Instant.EPOCH)
            payment.status shouldBe PaymentOrderStatus.CAPTURED
            payment.markRefundSucceeded(refund.id, "provider-refund-1", Instant.EPOCH)

            payment.status shouldBe PaymentOrderStatus.PARTIALLY_REFUNDED
            payment.refunds.single().status shouldBe PaymentRefundStatus.SUCCEEDED
            payment.pendingDomainEvents().last()::class shouldBe PaymentRefundSucceededEvent::class
        }
    })
