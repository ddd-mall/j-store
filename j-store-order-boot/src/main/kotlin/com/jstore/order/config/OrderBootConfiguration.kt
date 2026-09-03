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
package com.jstore.order.config

import com.jstore.common.framework.event.*
import com.jstore.common.geo.GeoAddressService
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.goods.api.GoodsSnapshotQueryService
import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessageHandler
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.order.acl.GoodsService
import com.jstore.order.acl.GoodsServiceImpl
import com.jstore.order.acl.OfferService
import com.jstore.order.acl.OfferServiceImpl
import com.jstore.order.acl.UserService
import com.jstore.order.acl.UserServiceImpl
import com.jstore.order.api.OrderAccountingQuery
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderFactoryImpl
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.TrustedOrderFactory
import com.jstore.order.domain.order.TrustedOrderFactoryImpl
import com.jstore.order.service.AfterSaleAccessService
import com.jstore.order.service.AfterSaleAccessUseCase
import com.jstore.order.service.AfterSaleApplicationService
import com.jstore.order.service.AfterSaleUseCase
import com.jstore.order.service.CancelOrderFromTradeIntegrationCommandHandler
import com.jstore.order.service.CreateOrderFromTradeIntegrationCommandHandler
import com.jstore.order.service.FulfillmentDeliveredOrderHandler
import com.jstore.order.service.FulfillmentDispatchedOrderHandler
import com.jstore.order.service.FulfillmentPreparedOrderHandler
import com.jstore.order.service.InternalOrderCreationUseCase
import com.jstore.order.service.OrderAccountingQueryService
import com.jstore.order.service.OrderService
import com.jstore.order.service.OrderUseCase
import com.jstore.order.service.PaymentCapturedOrderHandler
import com.jstore.order.service.PaymentRefundFailedOrderHandler
import com.jstore.order.service.PaymentRefundSucceededOrderHandler
import com.jstore.shop.api.MerchantAuthorizationQuery
import com.jstore.shop.api.OfferSnapshotQueryService
import com.jstore.user.api.UserProfileQueryService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class OrderBootConfiguration {
    @Bean
    fun orderAccountingQuery(orders: OrderRepository): OrderAccountingQuery =
        OrderAccountingQueryService(orders)

    @Bean
    fun snowFlakSequence(): SnowFlakSequence {
        return SnowFlakSequence()
    }

    @Bean
    fun goodsService(goodsSnapshotQueryService: GoodsSnapshotQueryService): GoodsService {
        return GoodsServiceImpl(goodsSnapshotQueryService)
    }

    @Bean
    fun offerService(offerSnapshotQueryService: OfferSnapshotQueryService): OfferService =
        OfferServiceImpl(offerSnapshotQueryService)

    @Bean
    fun orderUserService(
        userProfileQueryService: UserProfileQueryService,
        @org.springframework.beans.factory.annotation.Value($$"${jwt.issuer}")
        authenticationDomain: String,
    ): UserService = UserServiceImpl(userProfileQueryService, authenticationDomain)

    @Bean
    fun orderFactory(
        snowFlakSequence: SnowFlakSequence,
        goodsService: GoodsService,
        geoAddressService: GeoAddressService,
        offerService: OfferService,
    ): OrderFactory {
        return OrderFactoryImpl(
            snowFlakSequence,
            goodsService,
            geoAddressService,
            offerService,
        )
    }

    @Bean
    fun orderApplicationService(
        orderFactory: OrderFactory,
        orderRepository: OrderRepository,
        domainEventPublisher: DomainEventPublisher,
        userService: UserService,
        trustedOrderFactory: TrustedOrderFactory,
    ): OrderService {
        return OrderService(
            orderFactory,
            orderRepository,
            domainEventPublisher,
            userService,
            trustedOrderFactory,
        )
    }

    @Bean
    fun trustedOrderFactory(snowFlakSequence: SnowFlakSequence): TrustedOrderFactory =
        TrustedOrderFactoryImpl(snowFlakSequence)

    @Bean
    @Primary
    fun transactionalOrderUseCase(
        orderApplicationService: OrderService,
        transactionManager: PlatformTransactionManager,
    ): OrderUseCase = TransactionalOrderUseCase(orderApplicationService, transactionManager)

    @Bean
    fun internalOrderCreationUseCase(
        orderApplicationService: OrderService,
        transactionManager: PlatformTransactionManager,
    ): InternalOrderCreationUseCase =
        TransactionalInternalOrderCreationUseCase(orderApplicationService, transactionManager)

    @Bean
    fun createOrderFromTradeCommandHandler(
        orderApplicationService: OrderService,
        publisher: IntegrationMessagePublisher,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<
        com.jstore.contracts.commerce.CreateOrderFromTradeIntegrationCommand
    > =
        transactional(
            CreateOrderFromTradeIntegrationCommandHandler(orderApplicationService, publisher),
            transactionManager,
        )

    @Bean
    fun cancelOrderFromTradeCommandHandler(
        orderApplicationService: OrderService,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<
        com.jstore.contracts.commerce.CancelOrderFromTradeIntegrationCommand
    > =
        transactional(
            CancelOrderFromTradeIntegrationCommandHandler(orderApplicationService),
            transactionManager,
        )

    @Bean
    fun paymentCapturedOrderHandler(service: OrderUseCase) = PaymentCapturedOrderHandler(service)

    @Bean
    fun fulfillmentPreparedOrderHandler(service: OrderUseCase) =
        FulfillmentPreparedOrderHandler(service)

    @Bean
    fun fulfillmentDispatchedOrderHandler(service: OrderUseCase) =
        FulfillmentDispatchedOrderHandler(service)

    @Bean
    fun fulfillmentDeliveredOrderHandler(service: OrderUseCase) =
        FulfillmentDeliveredOrderHandler(service)

    @Bean
    fun paymentRefundSucceededOrderHandler(
        afterSales: AfterSaleUseCase,
        orders: OrderUseCase,
    ) = PaymentRefundSucceededOrderHandler(afterSales, orders)

    @Bean
    fun paymentRefundFailedOrderHandler(afterSales: AfterSaleUseCase) =
        PaymentRefundFailedOrderHandler(afterSales)

    @Bean
    fun afterSaleFactory(snowFlakSequence: SnowFlakSequence): AfterSaleFactory =
        AfterSaleFactoryImpl(snowFlakSequence)

    @Bean
    fun afterSaleApplicationService(
        factory: AfterSaleFactory,
        repository: AfterSaleRepository,
        refundCapacityRepository: RefundCapacityRepository,
        receiptStore: AfterSaleCommandReceiptStore,
        orderRepository: OrderRepository,
        domainEventPublisher: DomainEventPublisher,
    ) =
        AfterSaleApplicationService(
            factory,
            repository,
            refundCapacityRepository,
            receiptStore,
            orderRepository,
            domainEventPublisher,
        )

    @Bean
    @Primary
    fun transactionalAfterSaleUseCase(
        afterSaleApplicationService: AfterSaleApplicationService,
        transactionManager: PlatformTransactionManager,
    ): AfterSaleUseCase =
        TransactionalAfterSaleUseCase(afterSaleApplicationService, transactionManager)

    @Bean
    fun afterSaleAccessUseCase(
        afterSaleUseCase: AfterSaleUseCase,
        authorization: MerchantAuthorizationQuery,
    ): AfterSaleAccessUseCase = AfterSaleAccessService(afterSaleUseCase, authorization)

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
