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
import com.jstore.contracts.commerce.CreatePaymentForOrderCommand
import com.jstore.contracts.commerce.InventoryReservedIntegrationEvent
import com.jstore.contracts.commerce.StartTradeProcessCommand
import com.jstore.inventory.domain.event.StockReservedEvent
import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.order.domain.order.MerchantId
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.event.OrderCreatedEvent
import com.jstore.order.domain.order.event.OrderItemSnapshot
import com.jstore.order.domain.order.event.OrderTradeCommittedEvent
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OrderReservationTranslatorsTest {
    @Test
    fun `order creation starts a trade process with immutable sale snapshot`() {
        val publisher = CapturingPublisher()
        val event =
            OrderCreatedEvent(
                orderId = OrderId(42),
                merchantId = MerchantId(7),
                payableAmount = Price.ofFen(19800),
                currency = "CNY",
                items =
                    listOf(
                        OrderItemSnapshot(
                            offerId = 7001,
                            storeId = 71,
                            spuId = 501,
                            skuId = 1001,
                            quantity = 2,
                            catalogSnapshotVersion = 9,
                            offerVersion = 4,
                            fulfillmentNodeId = "CN-NORTH-1",
                            channelId = "ONLINE",
                            unitPrice = Price.ofFen(9900),
                        )
                    ),
                occurredAt = Instant.EPOCH,
            )

        OrderCreatedToTradeTranslator(publisher).onDomainEvent(event)

        val command = assertIs<StartTradeProcessCommand>(publisher.messages.single())
        assertEquals(1, command.messageVersion)
        assertEquals(501, command.items.single().spuId)
        assertEquals(9, command.items.single().catalogSnapshotVersion)
        assertEquals(4, command.items.single().offerVersion)
        assertEquals(9900, command.items.single().unitPriceFen)
        assertEquals(19800, command.payableAmountFen)
        assertEquals("trade.commands", command.destination)
    }

    @Test
    fun `payment creation waits for stock confirmation fact`() {
        val publisher = CapturingPublisher()
        val event =
            OrderTradeCommittedEvent(
                orderId = OrderId(42),
                merchantId = MerchantId(7),
                payableAmount = Price.ofFen(19800),
                currency = "CNY",
                occurredAt = Instant.EPOCH,
            )

        OrderTradeCommittedToPaymentTranslator(publisher).onDomainEvent(event)

        assertIs<CreatePaymentForOrderCommand>(publisher.messages.single())
    }

    @Test
    fun `reserved stock expiry is preserved across the context boundary`() {
        val publisher = CapturingPublisher()
        val expiry = Instant.parse("2026-08-05T00:30:00Z")
        val event =
            StockReservedEvent(
                orderId = 42,
                authorizationIds = listOf("auth-1"),
                reservationIds = listOf("reservation-1"),
                reservationExpiresAt = expiry,
                occurredAt = Instant.EPOCH,
            )

        StockReservedToOrderConfirmedTranslator(publisher).onDomainEvent(event)

        val integrationEvent =
            assertIs<InventoryReservedIntegrationEvent>(publisher.messages.single())
        assertEquals(1, integrationEvent.messageVersion)
        assertEquals(expiry, integrationEvent.reservationExpiresAt)
        assertEquals("trade.events", integrationEvent.destination)
    }

    private class CapturingPublisher : IntegrationMessagePublisher {
        val messages = mutableListOf<IntegrationMessage>()

        override fun publish(message: IntegrationMessage) {
            messages += message
        }
    }
}
