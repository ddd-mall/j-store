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
import com.jstore.order.acl.GoodsService
import com.jstore.order.acl.GoodsServiceImpl
import com.jstore.order.acl.OfferService
import com.jstore.order.acl.OfferServiceImpl
import com.jstore.order.acl.UserService
import com.jstore.order.acl.UserServiceImpl
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderFactoryImpl
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.service.AfterSaleApplicationService
import com.jstore.order.service.AfterSaleUseCase
import com.jstore.order.service.FulfillmentDeliveredOrderHandler
import com.jstore.order.service.FulfillmentDispatchedOrderHandler
import com.jstore.order.service.FulfillmentPreparedOrderHandler
import com.jstore.order.service.OrderService
import com.jstore.order.service.OrderTradeCommitmentFailedEventHandler
import com.jstore.order.service.OrderTradeCommittedEventHandler
import com.jstore.order.service.OrderUseCase
import com.jstore.order.service.PaymentCapturedOrderHandler
import com.jstore.order.service.PaymentRefundFailedOrderHandler
import com.jstore.order.service.PaymentRefundSucceededOrderHandler
import com.jstore.shop.api.OfferSnapshotQueryService
import com.jstore.user.api.UserProfileQueryService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class OrderBootConfiguration {
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
    fun orderUserService(userProfileQueryService: UserProfileQueryService): UserService =
        UserServiceImpl(userProfileQueryService)

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
    ): OrderService {
        return OrderService(
            orderFactory,
            orderRepository,
            domainEventPublisher,
            userService,
        )
    }

    @Bean
    @Primary
    fun transactionalOrderUseCase(
        orderApplicationService: OrderService,
        transactionManager: PlatformTransactionManager,
    ): OrderUseCase = TransactionalOrderUseCase(orderApplicationService, transactionManager)

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
    fun orderTradeCommittedHandler(service: OrderUseCase) = OrderTradeCommittedEventHandler(service)

    @Bean
    fun orderTradeCommitmentFailedHandler(service: OrderUseCase) =
        OrderTradeCommitmentFailedEventHandler(service)

    @Bean
    fun afterSaleFactory(snowFlakSequence: SnowFlakSequence): AfterSaleFactory =
        AfterSaleFactoryImpl(snowFlakSequence)

    @Bean
    fun afterSaleApplicationService(
        factory: AfterSaleFactory,
        repository: AfterSaleRepository,
        orderRepository: OrderRepository,
        domainEventPublisher: DomainEventPublisher,
    ) = AfterSaleApplicationService(factory, repository, orderRepository, domainEventPublisher)

    @Bean
    @Primary
    fun transactionalAfterSaleUseCase(
        afterSaleApplicationService: AfterSaleApplicationService,
        transactionManager: PlatformTransactionManager,
    ): AfterSaleUseCase =
        TransactionalAfterSaleUseCase(afterSaleApplicationService, transactionManager)
}
