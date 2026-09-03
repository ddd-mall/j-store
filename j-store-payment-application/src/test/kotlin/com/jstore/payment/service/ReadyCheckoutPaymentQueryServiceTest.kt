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

import com.jstore.common.properties.Price
import com.jstore.payment.domain.payment.PaymentAllocationSnapshot
import com.jstore.payment.domain.payment.TradePayment
import com.jstore.payment.domain.payment.TradePaymentId
import com.jstore.payment.domain.payment.TradePaymentRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReadyCheckoutPaymentQueryServiceTest {
    private val acceptedAt = Instant.parse("2029-01-01T00:00:00Z")

    @Test
    fun `only an unexpired ready action is exposed`() {
        val payment =
            TradePayment.prepare(
                    TradePaymentId(8001),
                    9001,
                    9901,
                    "FULL",
                    Price.ofFen(1000),
                    "CNY",
                    listOf(PaymentAllocationSnapshot(9101, 7001, 7, Price.ofFen(1000))),
                    acceptedAt,
                )
                .also {
                    it.markReady(
                        "provider-1",
                        "opaque-payment-action",
                        acceptedAt,
                        acceptedAt.plusSeconds(600),
                        acceptedAt.plusSeconds(300),
                    )
                }
        val repository = SinglePaymentRepository(payment)

        assertNotNull(ReadyCheckoutPaymentQueryService(repository) { acceptedAt }.find(8001))
        assertNull(
            ReadyCheckoutPaymentQueryService(repository) { acceptedAt.plusSeconds(300) }.find(8001)
        )
    }
}

private class SinglePaymentRepository(private var payment: TradePayment?) : TradePaymentRepository {
    override fun save(aggregate: TradePayment): TradePayment = aggregate.also { payment = it }

    override fun findById(id: TradePaymentId): TradePayment? = payment?.takeIf { it.id == id }

    override fun findByInstallment(settlementPlanId: Long, installmentId: String): TradePayment? =
        payment?.takeIf {
            it.settlementPlanId == settlementPlanId && it.installmentId == installmentId
        }
}
