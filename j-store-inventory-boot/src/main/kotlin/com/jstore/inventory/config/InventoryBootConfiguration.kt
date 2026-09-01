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
import com.jstore.contracts.commerce.ConfirmInventoryCommand
import com.jstore.contracts.commerce.PhysicalStockChangedIntegrationEvent
import com.jstore.contracts.commerce.ReleaseInventoryCommand
import com.jstore.contracts.commerce.ReserveInventoryCommand
import com.jstore.inventory.domain.StockPositionGuard
import com.jstore.inventory.domain.StockPositionRepository
import com.jstore.inventory.domain.StockReservationRepository
import com.jstore.inventory.service.ConfirmInventoryCommandHandler
import com.jstore.inventory.service.InventoryService
import com.jstore.inventory.service.InventoryAvailabilityQueryServiceImpl
import com.jstore.inventory.service.PhysicalStockChangedHandler
import com.jstore.inventory.service.ReleaseInventoryCommandHandler
import com.jstore.inventory.service.ReserveInventoryCommandHandler
import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessageHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class InventoryBootConfiguration {
    @Bean
    fun inventoryAvailabilityQueryService(positions: StockPositionRepository) =
        InventoryAvailabilityQueryServiceImpl(positions)

    @Bean
    fun inventoryService(
        guard: StockPositionGuard,
        positions: StockPositionRepository,
        reservations: StockReservationRepository,
    ) = InventoryService(guard, positions, reservations)

    @Bean
    fun reserveInventoryHandler(
        service: InventoryService,
        publisher: DomainEventPublisher,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<ReserveInventoryCommand> =
        transactional(ReserveInventoryCommandHandler(service, publisher), transactionManager)

    @Bean
    fun confirmInventoryHandler(
        service: InventoryService,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<ConfirmInventoryCommand> =
        transactional(ConfirmInventoryCommandHandler(service), transactionManager)

    @Bean
    fun releaseInventoryHandler(
        service: InventoryService,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<ReleaseInventoryCommand> =
        transactional(ReleaseInventoryCommandHandler(service), transactionManager)

    @Bean
    fun physicalStockChangedHandler(
        service: InventoryService,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<PhysicalStockChangedIntegrationEvent> =
        transactional(PhysicalStockChangedHandler(service), transactionManager)

    private fun <T : IntegrationMessage> transactional(
        delegate: IntegrationMessageHandler<T>,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<T> =
        object : IntegrationMessageHandler<T> {
            private val transaction = TransactionTemplate(transactionManager)

            override fun handlerId() = delegate.handlerId()

            override fun handle(message: T) {
                transaction.executeWithoutResult { delegate.handle(message) }
            }
        }
}
