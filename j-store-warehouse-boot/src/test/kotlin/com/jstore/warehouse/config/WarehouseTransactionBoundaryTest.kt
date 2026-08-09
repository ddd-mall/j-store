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
package com.jstore.warehouse.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.utils.Success
import com.jstore.warehouse.domain.PhysicalStock
import com.jstore.warehouse.domain.PhysicalStockId
import com.jstore.warehouse.domain.PhysicalStockRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager

class WarehouseTransactionBoundaryTest {
    @Test
    fun `stock adjustment saves and publishes inside one transaction`() {
        val transactionManager = RecordingTransactionManager()
        val repository =
            object : PhysicalStockRepository {
                override fun save(aggregate: PhysicalStock): PhysicalStock {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive())
                    return aggregate
                }

                override fun findById(id: PhysicalStockId) = PhysicalStock(id, 61, "WH-1", 10)
            }
        val publisher =
            object : DomainEventPublisher {
                override fun publishEvent(event: com.jstore.common.framework.event.DomainEvent) {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive())
                }
            }
        val useCase =
            WarehouseBootConfiguration()
                .warehouseStockService(repository, publisher, transactionManager)

        assertTrue(useCase.adjust(PhysicalStockId("61@WH-1"), 9, "count") is Success)
        assertEquals(1, transactionManager.commits)
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
