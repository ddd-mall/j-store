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
package com.jstore.translator

import com.jstore.common.framework.messaging.IntegrationMessage
import com.jstore.common.framework.messaging.IntegrationMessagePublisher
import com.jstore.common.properties.Price
import com.jstore.contracts.commerce.CreatePaymentForOrderCommand
import com.jstore.order.domain.order.MerchantId
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.event.OrderStockConfirmedEvent
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class OrderStockBeforePaymentTranslatorTest {
    @Test
    fun `stock-confirmed order creates payment command`() {
        val publisher = CapturingPublisher()
        val event =
            OrderStockConfirmedEvent(
                orderId = OrderId(1),
                merchantId = MerchantId(7),
                payableAmount = Price.ofFen(200),
                currency = "CNY",
                occurredAt = Instant.EPOCH,
                eventId = "stock-confirmed-1",
            )

        OrderStockConfirmedToPaymentTranslator(publisher).onDomainEvent(event)

        val command = assertIs<CreatePaymentForOrderCommand>(publisher.messages.single())
        assertEquals(1, command.orderId)
        assertEquals(7, command.merchantId)
        assertEquals(200, command.payableAmountFen)
        assertEquals("CNY", command.currency)
        assertEquals("stock-confirmed-1", command.sourceMessageId)
        assertEquals(Instant.EPOCH, command.occurredAtValue)
    }

    private class CapturingPublisher : IntegrationMessagePublisher {
        val messages = mutableListOf<IntegrationMessage>()

        override fun publish(message: IntegrationMessage) {
            messages += message
        }
    }
}
