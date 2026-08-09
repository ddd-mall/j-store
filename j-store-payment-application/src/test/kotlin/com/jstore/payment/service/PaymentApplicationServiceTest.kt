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

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.payment.domain.payment.PaymentErrors
import com.jstore.payment.domain.payment.PaymentOrder
import com.jstore.payment.domain.payment.PaymentOrderId
import com.jstore.payment.domain.payment.PaymentOrderImpl
import com.jstore.payment.domain.payment.PaymentOrderRepository
import com.jstore.payment.domain.payment.PaymentRefundId
import com.jstore.payment.domain.payment.event.PaymentCapturedEvent
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PaymentApplicationServiceTest {
    @Test
    fun `capture persists aggregate and publishes its domain event`() {
        val payment =
            PaymentOrderImpl(
                PaymentOrderId(1),
                orderId = 9,
                merchantId = 7,
                payableAmount = Price.ofFen(100),
                currency = "CNY",
            )
        val repository = FakeRepository(payment)
        val published = mutableListOf<DomainEvent>()
        val service =
            PaymentApplicationService(
                repository,
                SnowFlakSequence(1, 1),
                object : DomainEventPublisher {
                    override fun publishEvent(event: DomainEvent) {
                        published += event
                    }
                },
            )

        val result =
            service.capture(
                PaymentCaptureCommand(9, "provider-1", Price.ofFen(100), "CNY"),
                Instant.EPOCH,
            )

        assertEquals(true, assertIs<Success<Boolean>>(result).value)
        assertEquals(1, repository.saveCount)
        assertIs<PaymentCapturedEvent>(published.single())
    }

    @Test
    fun `capture propagates not found as a business failure`() {
        val service =
            PaymentApplicationService(
                FakeRepository(null),
                SnowFlakSequence(1, 1),
                object : DomainEventPublisher {
                    override fun publishEvent(event: DomainEvent) = Unit
                },
            )

        val result =
            service.capture(
                PaymentCaptureCommand(9, "provider-1", Price.ofFen(100), "CNY"),
                Instant.EPOCH,
            )

        assertEquals(PaymentErrors.ORDER_NOT_FOUND, assertIs<Failure<*>>(result).error)
    }

    private class FakeRepository(initial: PaymentOrder?) : PaymentOrderRepository {
        private var payment = initial
        var saveCount = 0
            private set

        override fun save(aggregate: PaymentOrder): PaymentOrder = aggregate.also {
            payment = it
            saveCount++
        }

        override fun findById(id: PaymentOrderId) = payment?.takeIf { it.id == id }

        override fun findByOrderId(orderId: Long) = payment?.takeIf { it.orderId == orderId }

        override fun findByRefundId(refundId: PaymentRefundId): PaymentOrder? = null
    }
}
