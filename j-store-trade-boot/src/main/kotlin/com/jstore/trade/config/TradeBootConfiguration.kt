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
package com.jstore.trade.config

import com.jstore.contracts.commerce.*
import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessageHandler
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.trade.domain.TradeProcessRepository
import com.jstore.trade.service.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class TradeBootConfiguration {
    @Bean
    fun tradeProcessUseCase(
        processes: TradeProcessRepository,
        publisher: IntegrationMessagePublisher,
    ): TradeProcessUseCase = TradeProcessApplicationService(processes, publisher)

    @Bean
    fun startTradeProcessHandler(
        trades: TradeProcessUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<StartTradeProcessCommand> =
        transactional(StartTradeProcessHandler(trades), transactionManager)

    @Bean
    fun saleAuthorizedTradeHandler(
        trades: TradeProcessUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<SaleAuthorizedIntegrationEvent> =
        transactional(SaleAuthorizedTradeHandler(trades), transactionManager)

    @Bean
    fun saleAuthorizationFailedTradeHandler(
        trades: TradeProcessUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<SaleAuthorizationFailedIntegrationEvent> =
        transactional(SaleAuthorizationFailedTradeHandler(trades), transactionManager)

    @Bean
    fun inventoryReservedTradeHandler(
        trades: TradeProcessUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<InventoryReservedIntegrationEvent> =
        transactional(InventoryReservedTradeHandler(trades), transactionManager)

    @Bean
    fun inventoryReservationFailedTradeHandler(
        trades: TradeProcessUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<InventoryReservationFailedIntegrationEvent> =
        transactional(InventoryReservationFailedTradeHandler(trades), transactionManager)

    @Bean
    fun orderCancelledTradeHandler(
        trades: TradeProcessUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<OrderCancelledIntegrationEvent> =
        transactional(OrderCancelledTradeHandler(trades), transactionManager)

    @Bean
    fun orderPaidTradeHandler(
        trades: TradeProcessUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<OrderPaidIntegrationEvent> =
        transactional(OrderPaidTradeHandler(trades), transactionManager)

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
