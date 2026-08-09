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
import com.jstore.contracts.commerce.AuthorizeSaleCommand
import com.jstore.contracts.commerce.CreatePaymentForOrderCommand
import com.jstore.contracts.commerce.ReserveInventoryCommand
import com.jstore.order.domain.order.MerchantId
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.SaleAuthorizationRef
import com.jstore.order.domain.order.event.OrderCreatedEvent
import com.jstore.order.domain.order.event.OrderItemSnapshot
import com.jstore.order.domain.order.event.OrderSaleAuthorizedEvent
import com.jstore.order.domain.order.event.OrderStockConfirmedEvent
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OrderReservationTranslatorsTest {
    @Test
    fun `order creation requests versioned store sale authorization`() {
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

        OrderCreatedToSaleAuthorizationTranslator(publisher).onDomainEvent(event)

        val command = assertIs<AuthorizeSaleCommand>(publisher.messages.single())
        assertEquals(1, command.messageVersion)
        assertEquals(501, command.items.single().spuId)
        assertEquals(9, command.items.single().catalogSnapshotVersion)
        assertEquals(4, command.items.single().offerVersion)
        assertEquals(9900, command.items.single().unitPriceFen)
    }

    @Test
    fun `authorized sale requests ATP reservation with the exact durable authorization expiry`() {
        val publisher = CapturingPublisher()
        val expiry = Instant.parse("2026-08-05T00:15:00Z")
        val item =
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
        val event =
            OrderSaleAuthorizedEvent(
                OrderId(42),
                MerchantId(7),
                listOf(SaleAuthorizationRef("auth-1", 7001, expiry)),
                listOf(item),
                Instant.EPOCH,
            )

        OrderSaleAuthorizedToStockReservationTranslator(publisher).onDomainEvent(event)

        val command = assertIs<ReserveInventoryCommand>(publisher.messages.single())
        assertEquals(3, command.messageVersion)
        assertEquals("auth-1", command.items.single().authorizationId)
        assertEquals(expiry, command.items.single().expiresAt)
    }

    @Test
    fun `payment creation waits for stock confirmation fact`() {
        val publisher = CapturingPublisher()
        val event =
            OrderStockConfirmedEvent(
                orderId = OrderId(42),
                merchantId = MerchantId(7),
                payableAmount = Price.ofFen(19800),
                currency = "CNY",
                occurredAt = Instant.EPOCH,
            )

        OrderStockConfirmedToPaymentTranslator(publisher).onDomainEvent(event)

        assertIs<CreatePaymentForOrderCommand>(publisher.messages.single())
    }

    private class CapturingPublisher : IntegrationMessagePublisher {
        val messages = mutableListOf<IntegrationMessage>()

        override fun publish(message: IntegrationMessage) {
            messages += message
        }
    }
}
