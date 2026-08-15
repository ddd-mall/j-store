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
import com.jstore.common.utils.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    fun `prepared payment can be cancelled idempotently before provider execution`() {
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

        assertEquals(true, (payment.cancel("buyer cancelled") as Success).value)
        assertEquals(TradePaymentStatus.CANCELLED, payment.status)
        assertEquals(false, (payment.cancel("buyer cancelled") as Success).value)
    }
}
