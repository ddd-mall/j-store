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

import com.jstore.common.geo.GeoAddressService
import com.jstore.cart.api.CartCheckoutSourceQueryService
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.contracts.commerce.*
import com.jstore.goods.api.GoodsSnapshotQueryService
import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessageHandler
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.shop.api.OfferSnapshotQueryService
import com.jstore.trade.domain.TradeRepository
import com.jstore.trade.service.*
import com.jstore.user.api.UserProfileQueryService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class TradeBootConfiguration {
    @Bean
    fun tradeOrderCreationGateway(
        publisher: IntegrationMessagePublisher
    ): TradeOrderCreationGateway = TradeOrderMessageGateway(publisher)

    @Bean
    fun tradeSettlementGateway(publisher: IntegrationMessagePublisher): TradeSettlementGateway =
        TradeSettlementMessageGateway(publisher)

    @Bean
    fun checkoutPreparationGateway(
        offers: OfferSnapshotQueryService,
        goods: GoodsSnapshotQueryService,
        users: UserProfileQueryService,
        addresses: GeoAddressService,
    ): CheckoutPreparationGateway = OfferCheckoutPreparationAdapter(offers, goods, users, addresses)

    @Bean
    fun checkoutUseCase(
        preparation: CheckoutPreparationGateway,
        trades: TradeRepository,
        sequence: SnowFlakSequence,
        publisher: IntegrationMessagePublisher,
        payments: CheckoutPaymentGateway,
        cartSources: CartCheckoutSourceQueryService,
        transactionManager: PlatformTransactionManager,
    ): CheckoutUseCase =
        TransactionalCheckoutUseCase(
            CheckoutApplicationService(
                preparation,
                trades,
                { sequence.nextId() },
                TradeAuthorizationMessageGateway(publisher),
                payments,
                CartCheckoutSourceAdapter(cartSources),
            ),
            transactionManager,
        )

    @Bean
    fun tradeSagaUseCase(
        trades: TradeRepository,
        sequence: SnowFlakSequence,
        orders: TradeOrderCreationGateway,
        settlement: TradeSettlementGateway,
        publisher: IntegrationMessagePublisher,
    ): TradeSagaUseCase =
        TradeSagaApplicationService(
            trades,
            { sequence.nextId() },
            orders,
            settlement,
            publisher,
        )

    @Bean
    fun orderCreatedTradeHandler(
        trades: TradeSagaUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<OrderCreatedFromTradeIntegrationEvent> =
        transactional(TradeOrderCreatedHandler(trades), transactionManager)

    @Bean
    fun orderCreationRejectedTradeHandler(
        trades: TradeSagaUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<OrderCreationRejectedFromTradeIntegrationEvent> =
        transactional(TradeOrderCreationRejectedHandler(trades), transactionManager)

    @Bean
    fun saleAuthorizedTradeHandler(
        trades: TradeSagaUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<SaleAuthorizedIntegrationEvent> =
        transactional(TradePlanSaleAuthorizedHandler(trades), transactionManager)

    @Bean
    fun inventoryReservedTradeHandler(
        trades: TradeSagaUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<InventoryReservedIntegrationEvent> =
        transactional(TradePlanInventoryReservedHandler(trades), transactionManager)

    @Bean
    fun saleAuthorizationFailedTradeHandler(
        trades: TradeSagaUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<SaleAuthorizationFailedIntegrationEvent> =
        transactional(TradePlanSaleAuthorizationFailedHandler(trades), transactionManager)

    @Bean
    fun inventoryReservationFailedTradeHandler(
        trades: TradeSagaUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<InventoryReservationFailedIntegrationEvent> =
        transactional(TradePlanInventoryReservationFailedHandler(trades), transactionManager)

    @Bean
    fun orderCancelledTradeHandler(
        trades: TradeSagaUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<OrderCancelledIntegrationEvent> =
        transactional(TradeOrderCancelledHandler(trades), transactionManager)

    @Bean
    fun paymentPreparedTradeHandler(
        trades: TradeSagaUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<PaymentPreparedIntegrationEvent> =
        transactional(TradePaymentPreparedHandler(trades), transactionManager)

    @Bean
    fun paymentPreparationRejectedTradeHandler(
        trades: TradeSagaUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<PaymentPreparationRejectedIntegrationEvent> =
        transactional(TradePaymentPreparationRejectedHandler(trades), transactionManager)

    @Bean
    fun paymentPreparationUncertainTradeHandler(
        trades: TradeSagaUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<PaymentPreparationUncertainIntegrationEvent> =
        transactional(TradePaymentPreparationUncertainHandler(trades), transactionManager)

    @Bean
    fun paymentCancellationConfirmedTradeHandler(
        trades: TradeSagaUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<PaymentCancellationConfirmedIntegrationEvent> =
        transactional(TradePaymentCancellationConfirmedHandler(trades), transactionManager)

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
