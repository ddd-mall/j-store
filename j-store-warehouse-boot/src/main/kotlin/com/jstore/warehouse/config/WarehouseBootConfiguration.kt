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
import com.jstore.warehouse.domain.PhysicalStockId
import com.jstore.warehouse.domain.PhysicalStockRepository
import com.jstore.warehouse.service.WarehouseStockService
import com.jstore.warehouse.service.WarehouseStockUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class WarehouseBootConfiguration {
    @Bean
    fun warehouseStockService(
        stocks: PhysicalStockRepository,
        publisher: DomainEventPublisher,
        transactionManager: PlatformTransactionManager,
    ): WarehouseStockUseCase {
        val delegate = WarehouseStockService(stocks, publisher)
        val transaction = TransactionTemplate(transactionManager)
        return object : WarehouseStockUseCase {
            override fun adjust(
                stockId: PhysicalStockId,
                quantity: Int,
                reason: String,
            ) = requireNotNull(transaction.execute { delegate.adjust(stockId, quantity, reason) })
        }
    }
}
