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
package com.jstore.inventory.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.contracts.commerce.ReserveInventoryCommand
import com.jstore.inventory.domain.FulfillmentNodeId
import com.jstore.inventory.domain.SkuId
import com.jstore.inventory.domain.StockPosition
import com.jstore.inventory.domain.StockPositionGuard
import com.jstore.inventory.domain.StockPositionId
import com.jstore.inventory.domain.StockPositionRepository
import com.jstore.inventory.domain.StockReservation
import com.jstore.inventory.domain.StockReservationId
import com.jstore.inventory.domain.StockReservationRepository
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager

class InventoryTransactionBoundaryTest {
    @Test
    fun `reserve handler publishes its outcome inside a transaction`() {
        val transactionManager = RecordingTransactionManager()
        val publisher =
            object : DomainEventPublisher {
                override fun publishEvent(event: com.jstore.common.framework.event.DomainEvent) {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive())
                }
            }
        val service =
            InventoryBootConfiguration()
                .inventoryService(
                    StockPositionGuard { emptyList() },
                    EmptyPositions,
                    EmptyReservations,
                )
        val handler =
            InventoryBootConfiguration()
                .reserveInventoryHandler(service, publisher, transactionManager)

        handler.handle(ReserveInventoryCommand(1, emptyList(), "source", 2, Instant.EPOCH))

        assertEquals(1, transactionManager.commits)
    }

    private object EmptyPositions : StockPositionRepository {
        override fun save(aggregate: StockPosition) = aggregate

        override fun findById(id: StockPositionId): StockPosition? = null

        override fun findBySkuAndNode(
            skuId: SkuId,
            nodeId: FulfillmentNodeId,
        ): StockPosition? = null
    }

    private object EmptyReservations : StockReservationRepository {
        override fun save(aggregate: StockReservation) = aggregate

        override fun findById(id: StockReservationId): StockReservation? = null

        override fun findByBusinessKey(businessKey: String): StockReservation? = null

        override fun findByOrderId(orderId: Long): List<StockReservation> = emptyList()
    }

    private class RecordingTransactionManager : AbstractPlatformTransactionManager() {
        var commits = 0

        override fun doGetTransaction() = Any()

        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit

        override fun doCommit(status: DefaultTransactionStatus) {
            commits++
        }

        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
