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
package com.jstore.trade.service

import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.*
import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.trade.domain.TradeProcess
import com.jstore.trade.domain.TradeProcessId
import com.jstore.trade.domain.TradeProcessRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TradeProcessApplicationServiceTest {
    private val repository = MemoryTradeProcessRepository()
    private val publisher = CapturingPublisher()
    private val service = TradeProcessApplicationService(repository, publisher)
    private val now = Instant.parse("2026-08-14T10:00:00Z")
    private val expiresAt = now.plusSeconds(900)

    @Test
    fun `start persists process and requests sale authorization once`() {
        val command = startCommand()

        assertEquals(true, assertIs<Success<Boolean>>(service.start(command)).value)
        assertIs<AuthorizeSaleCommand>(publisher.messages.single())
        publisher.messages.clear()
        assertEquals(false, assertIs<Success<Boolean>>(service.start(command)).value)
        assertEquals(emptyList(), publisher.messages)
    }

    @Test
    fun `authorization requests inventory and reservation confirms commitment`() {
        service.start(startCommand())
        publisher.messages.clear()

        service.recordSaleAuthorized(authorizedEvent())
        val reserve = assertIs<ReserveInventoryCommand>(publisher.messages.single())
        assertEquals(expiresAt, reserve.acceptBefore)
        publisher.messages.clear()

        service.recordInventoryReserved(
            InventoryReservedIntegrationEvent(
                orderId = 100,
                authorizationIds = listOf("auth-1"),
                reservationIds = listOf("reservation-1"),
                reservationExpiresAt = expiresAt.minusSeconds(10),
                sourceMessageId = "inventory-event-1",
                occurredAtValue = now,
            )
        )
        assertIs<TradeCommitmentConfirmedIntegrationEvent>(publisher.messages.single())
    }

    @Test
    fun `reservation failure releases authorization and rejects commitment`() {
        service.start(startCommand())
        service.recordSaleAuthorized(authorizedEvent())
        publisher.messages.clear()

        service.recordInventoryReservationFailed(
            InventoryReservationFailedIntegrationEvent(
                orderId = 100,
                authorizationIds = listOf("auth-1"),
                reason = "no stock",
                sourceMessageId = "inventory-event-2",
                occurredAtValue = now,
            )
        )

        assertEquals(2, publisher.messages.size)
        assertIs<ReleaseSaleAuthorizationCommand>(publisher.messages[0])
        assertIs<TradeCommitmentFailedIntegrationEvent>(publisher.messages[1])
    }

    @Test
    fun `cancelling a committed trade releases inventory and sale authorization once`() {
        service.start(startCommand())
        service.recordSaleAuthorized(authorizedEvent())
        service.recordInventoryReserved(
            InventoryReservedIntegrationEvent(
                100,
                listOf("auth-1"),
                listOf("reservation-1"),
                expiresAt.minusSeconds(10),
                "inventory-event-1",
                now,
            )
        )
        publisher.messages.clear()
        val cancelled = OrderCancelledIntegrationEvent(100, "buyer cancelled", "cancel-1", now)

        service.close(cancelled)
        assertEquals(2, publisher.messages.size)
        assertIs<ReleaseInventoryCommand>(publisher.messages[0])
        assertIs<ReleaseSaleAuthorizationCommand>(publisher.messages[1])
        publisher.messages.clear()
        service.close(cancelled)
        assertEquals(emptyList(), publisher.messages)
    }

    private fun startCommand() =
        StartTradeProcessCommand(
            orderId = 100,
            merchantId = 7,
            items =
                listOf(
                    ContractSaleItem(
                        offerId = 10,
                        storeId = 3,
                        spuId = 20,
                        skuId = 21,
                        quantity = 2,
                        catalogSnapshotVersion = 4,
                        offerVersion = 5,
                        fulfillmentNodeId = "CN-NORTH-1",
                        channelId = "ONLINE",
                        unitPriceFen = 990,
                    )
                ),
            payableAmountFen = 1980,
            currency = "CNY",
            sourceMessageId = "order-event-1",
            occurredAtValue = now,
        )

    private fun authorizedEvent() =
        SaleAuthorizedIntegrationEvent(
            orderId = 100,
            items =
                listOf(
                    ContractAuthorizedSaleItem(
                        "auth-1",
                        10,
                        21,
                        2,
                        "CN-NORTH-1",
                        expiresAt,
                    )
                ),
            sourceMessageId = "store-event-1",
            occurredAtValue = now,
        )
}

private class MemoryTradeProcessRepository : TradeProcessRepository {
    private val values = mutableMapOf<TradeProcessId, TradeProcess>()

    override fun save(aggregate: TradeProcess): TradeProcess = aggregate.also { values[it.id] = it }

    override fun findById(id: TradeProcessId): TradeProcess? = values[id]
}

private class CapturingPublisher : IntegrationMessagePublisher {
    val messages = mutableListOf<IntegrationMessage>()

    override fun publish(message: IntegrationMessage) {
        messages += message
    }
}
