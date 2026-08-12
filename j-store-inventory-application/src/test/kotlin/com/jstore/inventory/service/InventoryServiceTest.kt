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
package com.jstore.inventory.service

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.ContractAuthorizedSaleItem
import com.jstore.contracts.commerce.PhysicalStockChangedIntegrationEvent
import com.jstore.contracts.commerce.ReserveInventoryCommand
import com.jstore.inventory.domain.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InventoryServiceTest {
    private val now = Instant.parse("2026-08-05T00:00:00Z")

    @Test
    fun `authorized reservation is persisted once and retry is idempotent`() {
        val positions = FakePositions(stock(onHand = 10, safety = 2, isolated = 1))
        val reservations = FakeReservations()
        val service = service(positions, reservations)
        val command = reserveCommand(expiresAt = now.plusSeconds(60), quantity = 3)

        val first = assertIs<Success<StockReservationResult>>(service.reserve(command))
        assertEquals(now.plusSeconds(60), first.value.expiresAt)
        assertEquals(3, positions.single().reserved)
        assertEquals(4, positions.single().availableToPromise)
        assertEquals(1, reservations.values.size)

        assertIs<Success<*>>(service.reserve(command))
        assertEquals(3, positions.single().reserved)
        assertEquals(1, reservations.values.size)
    }

    @Test
    fun `expired sale authorization cannot consume ATP`() {
        val positions = FakePositions(stock(onHand = 10))
        val reservations = FakeReservations()

        assertIs<Failure<*>>(
            service(positions, reservations).reserve(reserveCommand(expiresAt = now, quantity = 1))
        )
        assertEquals(0, positions.single().reserved)
        assertEquals(0, reservations.values.size)
    }

    @Test
    fun `command that missed its acceptance deadline cannot consume ATP`() {
        val positions = FakePositions(stock(onHand = 10))
        val reservations = FakeReservations()
        val command =
            reserveCommand(expiresAt = now.plusSeconds(60), quantity = 1).copy(acceptBefore = now)

        assertIs<Failure<*>>(service(positions, reservations).reserve(command))
        assertEquals(0, positions.single().reserved)
        assertEquals(0, reservations.values.size)
    }

    @Test
    fun `authorization expiring while waiting for stock lock cannot consume ATP`() {
        val positions = FakePositions(stock(onHand = 10))
        val reservations = FakeReservations()
        val clock = MutableClock(now)
        val service =
            InventoryService(
                positionGuard =
                    StockPositionGuard { ids ->
                        clock.advance(Duration.ofSeconds(61))
                        ids.mapNotNull(positions::findById)
                    },
                positions = positions,
                reservations = reservations,
                clock = clock,
            )

        assertIs<Failure<*>>(
            service.reserve(reserveCommand(expiresAt = now.plusSeconds(60), quantity = 1))
        )
        assertEquals(0, positions.single().reserved)
        assertEquals(0, reservations.values.size)
    }

    @Test
    fun `older WMS event cannot overwrite a newer stock mirror`() {
        val positions = FakePositions(stock(onHand = 10, sourceVersion = 7))
        val service = service(positions, FakeReservations())

        val result =
            service.applyPhysicalStock(
                PhysicalStockChangedIntegrationEvent(
                    skuId = 11,
                    fulfillmentNodeId = "CN-NORTH-1",
                    onHand = 99,
                    sourceVersion = 6,
                    reason = "COUNTED",
                    sourceMessageId = "wms-6",
                    occurredAtValue = now,
                )
            )

        assertEquals(false, (result as Success).value)
        assertEquals(10, positions.single().onHand)
        assertEquals(7, positions.single().sourceVersion)
    }

    private fun service(positions: FakePositions, reservations: FakeReservations) =
        InventoryService(
            positionGuard = StockPositionGuard { ids -> ids.mapNotNull(positions::findById) },
            positions = positions,
            reservations = reservations,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

    private fun stock(onHand: Int, safety: Int = 0, isolated: Int = 0, sourceVersion: Long = 1) =
        StockPosition(
            StockPositionId("11@CN-NORTH-1"),
            SkuId(11),
            FulfillmentNodeId("CN-NORTH-1"),
            onHand,
            safetyStock = safety,
            isolatedQuantity = isolated,
            sourceVersion = sourceVersion,
        )

    private fun reserveCommand(expiresAt: Instant, quantity: Int) =
        ReserveInventoryCommand(
            orderId = 100,
            items =
                listOf(
                    ContractAuthorizedSaleItem(
                        authorizationId = "ORDER-100-OFFER-1",
                        offerId = 1,
                        skuId = 11,
                        quantity = quantity,
                        fulfillmentNodeId = "CN-NORTH-1",
                        expiresAt = expiresAt,
                    )
                ),
            sourceMessageId = "sale-authorized-100",
            merchantId = 7,
            occurredAtValue = now,
        )
}

private class MutableClock(private var current: Instant) : Clock() {
    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    override fun instant(): Instant = current

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this
}

private class FakePositions(vararg initial: StockPosition) : StockPositionRepository {
    private val values = initial.associateBy { it.id }.toMutableMap()

    override fun save(aggregate: StockPosition): StockPosition = aggregate.also {
        values[it.id] = it
    }

    override fun findById(id: StockPositionId): StockPosition? = values[id]

    override fun findBySkuAndNode(skuId: SkuId, nodeId: FulfillmentNodeId): StockPosition? =
        values.values.singleOrNull { it.skuId == skuId && it.fulfillmentNodeId == nodeId }

    fun single() = values.values.single()
}

private class FakeReservations : StockReservationRepository {
    val values = linkedMapOf<StockReservationId, StockReservation>()

    override fun save(aggregate: StockReservation): StockReservation = aggregate.also {
        values[it.id] = it
    }

    override fun findById(id: StockReservationId): StockReservation? = values[id]

    override fun findByBusinessKey(businessKey: String): StockReservation? =
        values.values.singleOrNull { it.businessKey == businessKey }

    override fun findByOrderId(orderId: Long): List<StockReservation> =
        values.values.filter { it.orderId == orderId }
}
