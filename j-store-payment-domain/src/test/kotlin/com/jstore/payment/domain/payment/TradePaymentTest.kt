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
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TradePaymentTest {
    @Test
    fun `one payment installment can allocate its amount across orders`() {
        val payment =
            TradePayment.prepare(
                TradePaymentId(1),
                tradeId = 100,
                settlementPlanId = 200,
                installmentId = "FULL",
                payableAmount = Price.ofFen(3000),
                currency = "CNY",
                allocations =
                    listOf(
                        PaymentAllocationSnapshot(11, 21, 7, Price.ofFen(1000)),
                        PaymentAllocationSnapshot(12, 22, 8, Price.ofFen(2000)),
                    ),
            )

        assertEquals(100, payment.tradeId)
        assertEquals(2, payment.allocations.size)
        assertEquals(3000, payment.allocations.sumOf { it.amount.fen })
    }

    @Test
    fun `payment allocation must conserve installment amount`() {
        assertFailsWith<IllegalArgumentException> {
            TradePayment.prepare(
                TradePaymentId(1),
                100,
                200,
                "FULL",
                Price.ofFen(3000),
                "CNY",
                listOf(PaymentAllocationSnapshot(11, 21, 7, Price.ofFen(1000))),
            )
        }
    }

    @Test
    fun `prepared payment also waits for provider cancellation confirmation`() {
        val payment =
            TradePayment.prepare(
                TradePaymentId(1),
                100,
                200,
                "FULL",
                Price.ofFen(1000),
                "CNY",
                listOf(PaymentAllocationSnapshot(11, 21, 7, Price.ofFen(1000))),
            )

        assertEquals(true, (payment.requestCancellation("buyer cancelled") as Success).value)
        assertEquals(TradePaymentStatus.PREPARATION_CANCELLING, payment.status)
        assertEquals(false, (payment.requestCancellation("buyer cancelled") as Success).value)
        assertEquals("buyer cancelled", payment.cancellationReason)
        assertEquals(true, (payment.continueCancellationAfterPreparation() as Success).value)
        assertEquals(true, (payment.confirmCancellation() as Success).value)
        assertEquals(TradePaymentStatus.CANCELLED, payment.status)
    }

    @Test
    fun `only an explicitly accepted provider result exposes a short lived payment action`() {
        val payment = payment()
        val acceptedAt = Instant.parse("2029-01-01T00:00:00Z")
        val acceptBefore = acceptedAt.plusSeconds(600)
        val expiresAt = acceptedAt.plusSeconds(300)

        val first =
            payment.markReady(
                providerReference = "provider-1",
                payAction = "https://pay.example/short-lived-token",
                acceptedAt = acceptedAt,
                acceptBefore = acceptBefore,
                expiresAt = expiresAt,
            )
        val duplicate =
            payment.markReady(
                "provider-1",
                "https://pay.example/short-lived-token",
                acceptedAt,
                acceptBefore,
                expiresAt,
            )

        assertEquals(true, assertIs<Success<Boolean>>(first).value)
        assertEquals(false, assertIs<Success<Boolean>>(duplicate).value)
        assertEquals(TradePaymentStatus.READY, payment.status)
        assertEquals("provider-1", payment.providerReference)
        assertEquals(expiresAt, payment.expiresAt)
    }

    @Test
    fun `ready payment enters cancelling until provider confirms cancellation`() {
        val payment = payment()
        val acceptedAt = Instant.parse("2029-01-01T00:00:00Z")
        assertIs<Success<Boolean>>(
            payment.markReady(
                "provider-1",
                "opaque-payment-action",
                acceptedAt,
                acceptedAt.plusSeconds(600),
                acceptedAt.plusSeconds(300),
            )
        )

        assertEquals(
            true,
            assertIs<Success<Boolean>>(payment.requestCancellation("buyer cancelled")).value,
        )
        assertEquals(TradePaymentStatus.CANCELLING, payment.status)
        assertEquals(true, assertIs<Success<Boolean>>(payment.confirmCancellation()).value)
        assertEquals(TradePaymentStatus.CANCELLED, payment.status)
    }

    @Test
    fun `provider controlled text must fit the persisted contract`() {
        val acceptedAt = Instant.parse("2029-01-01T00:00:00Z")

        assertIs<Failure<*>>(
            payment()
                .markReady(
                    "r".repeat(257),
                    "action",
                    acceptedAt,
                    acceptedAt.plusSeconds(600),
                    acceptedAt.plusSeconds(300),
                )
        )
        assertIs<Failure<*>>(
            payment(2)
                .markReady(
                    "reference",
                    "a".repeat(2049),
                    acceptedAt,
                    acceptedAt.plusSeconds(600),
                    acceptedAt.plusSeconds(300),
                )
        )
        assertIs<Failure<*>>(payment(3).reject("r".repeat(1025)))
        assertIs<Failure<*>>(payment(4).markUncertain("r".repeat(1025)))
    }

    @Test
    fun `uncertain preparation remains distinct from explicit rejection`() {
        val uncertain = payment()
        val rejected = payment(2)

        assertIs<Success<Boolean>>(uncertain.markUncertain("provider timeout"))
        assertIs<Success<Boolean>>(rejected.reject("provider declined"))

        assertEquals(TradePaymentStatus.UNCERTAIN, uncertain.status)
        assertEquals(TradePaymentStatus.REJECTED, rejected.status)
        assertEquals(
            true,
            assertIs<Success<Boolean>>(uncertain.requestCancellation("buyer cancelled")).value,
        )
        assertEquals(TradePaymentStatus.CANCELLING, uncertain.status)
    }

    @Test
    fun `provider acceptance after cancellation was confirmed requires a fresh cancellation`() {
        val payment = payment()
        val acceptedAt = Instant.parse("2029-01-01T00:00:00Z")
        payment.requestCancellation("buyer cancelled")
        payment.continueCancellationAfterPreparation()
        payment.confirmCancellation()

        assertEquals(
            true,
            assertIs<Success<Boolean>>(
                    payment.recordLateProviderAcceptance(
                        "provider-1",
                        "opaque-payment-action",
                        acceptedAt,
                        acceptedAt.plusSeconds(600),
                        acceptedAt.plusSeconds(300),
                    )
                )
                .value,
        )

        assertEquals(TradePaymentStatus.CANCELLING, payment.status)
        assertEquals("provider-1", payment.providerReference)
        assertEquals("buyer cancelled", payment.cancellationReason)
    }

    @Test
    fun `provider diagnostics never replace the business cancellation reason`() {
        val payment = payment()
        payment.requestCancellation("buyer cancelled")
        payment.continueCancellationAfterPreparation()

        assertIs<Success<Boolean>>(payment.recordCancellationUncertain("provider timeout"))

        assertEquals("buyer cancelled", payment.cancellationReason)
        assertEquals("provider timeout", payment.failureReason)
    }

    private fun payment(id: Long = 1) =
        TradePayment.prepare(
            TradePaymentId(id),
            100,
            200,
            "FULL",
            Price.ofFen(1000),
            "CNY",
            listOf(PaymentAllocationSnapshot(11, 21, 7, Price.ofFen(1000))),
        )
}
