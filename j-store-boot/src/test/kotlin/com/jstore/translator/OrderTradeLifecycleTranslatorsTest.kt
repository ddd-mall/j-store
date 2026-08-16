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

import com.jstore.common.properties.Price
import com.jstore.contracts.commerce.ConfirmInventoryCommand
import com.jstore.contracts.commerce.OrderCancelledIntegrationEvent
import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.order.domain.order.MerchantId
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.event.OrderCancellationRequestedEvent
import com.jstore.order.domain.order.event.OrderItemSnapshot
import com.jstore.order.domain.order.event.OrderPaidEvent
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class OrderTradeLifecycleTranslatorsTest {
    @Test
    fun `buyer cancellation carries trade correlation back to Trade`() {
        val publisher = CapturingLifecyclePublisher()
        OrderCancelledToTradeTranslator(publisher)
            .onDomainEvent(
                OrderCancellationRequestedEvent(
                    OrderId(7001),
                    9001,
                    9101,
                    "buyer changed mind",
                    Instant.EPOCH,
                    "cancel-event",
                )
            )

        val event = assertIs<OrderCancelledIntegrationEvent>(publisher.messages.single())
        assertEquals(9001, event.tradeId)
        assertEquals(9101, event.orderPlanId)
        assertEquals(7001, event.orderId)
    }

    @Test
    fun `paid trade order confirms its plan inventory`() {
        val publisher = CapturingLifecyclePublisher()
        val repository = repository(orderId = 7001, tradeId = 9001, orderPlanId = 9101)

        OrderPaidToStockConfirmTranslator(repository, publisher)
            .onDomainEvent(
                OrderPaidEvent(
                    OrderId(7001),
                    MerchantId(7),
                    "payment-1",
                    Price.ofFen(1000),
                    "CNY",
                    listOf(OrderItemSnapshot(201, 101, 1, 1, Price.ofFen(1000))),
                    Instant.EPOCH,
                    "paid-event",
                )
            )

        val command = assertIs<ConfirmInventoryCommand>(publisher.messages.single())
        assertEquals(9001, command.tradeId)
        assertEquals(9101, command.orderPlanId)
        assertEquals("paid-event", command.sourceMessageId)
    }

    private fun repository(orderId: Long, tradeId: Long, orderPlanId: Long): OrderRepository {
        val order = mock(Order::class.java)
        `when`(order.sourceTradeId).thenReturn(tradeId)
        `when`(order.sourceOrderPlanId).thenReturn(orderPlanId)
        val repository = mock(OrderRepository::class.java)
        `when`(repository.findById(OrderId(orderId))).thenReturn(order)
        return repository
    }
}

private class CapturingLifecyclePublisher : IntegrationMessagePublisher {
    val messages = mutableListOf<IntegrationMessage>()

    override fun publish(message: IntegrationMessage) {
        messages += message
    }
}
