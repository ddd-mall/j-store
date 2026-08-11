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
import com.jstore.goods.service.AfterSaleStockRestoreEventHandler
import com.jstore.goods.service.InventoryService
import com.jstore.order.acl.GoodsService
import com.jstore.order.acl.GoodsServiceImpl
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderFactoryImpl
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.service.AfterSaleApplicationService
import com.jstore.order.service.OrderService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
    fun orderFactory(
        snowFlakSequence: SnowFlakSequence,
        goodsService: GoodsService,
        geoAddressService: GeoAddressService,
    ): OrderFactory {
        return OrderFactoryImpl(
            snowFlakSequence,
            goodsService,
            geoAddressService,
        )
    }

    @Bean
    fun orderService(
        orderFactory: OrderFactory,
        orderRepository: OrderRepository,
        domainEventPublisher: DomainEventPublisher,
    ): OrderService {
        return OrderService(
            orderFactory,
            orderRepository,
            domainEventPublisher,
        )
    }

    @Bean
    fun afterSaleFactory(snowFlakSequence: SnowFlakSequence): AfterSaleFactory =
        AfterSaleFactoryImpl(snowFlakSequence)

    @Bean
    fun afterSaleApplicationService(
        factory: AfterSaleFactory,
        repository: AfterSaleRepository,
        orderRepository: OrderRepository,
    ) = AfterSaleApplicationService(factory, repository, orderRepository)

    @Bean
    fun afterSaleStockRestoreEventHandler(inventoryServices: ObjectProvider<InventoryService>) =
        AfterSaleStockRestoreEventHandler {
            inventoryServices.getIfAvailable()
        }

    @Bean
    fun springDomainEventListenerRegistry(
        applicationContext: ConfigurableApplicationContext,
        consumptionRepositoryProvider: ObjectProvider<DomainEventConsumptionRepository>,
    ): SpringDomainEventListenerRegistry {
        return SpringDomainEventListenerRegistry(
            applicationContext,
            consumptionRepositoryProvider.getIfAvailable() ?: NoopDomainEventConsumptionRepository,
        )
    }

    @Bean
    fun springDomainEventBus(
        springDomainEventRegistry: SpringDomainEventListenerRegistry,
        applicationEventPublisher: ApplicationEventPublisher,
    ): SpringDomainEventBus {
        return SpringDomainEventBus(springDomainEventRegistry, applicationEventPublisher)
    }

    @Bean
    fun springDomainEventListenerRegistrationMachine(
        springDomainEventBus: SpringDomainEventBus,
        domainEventListeners: List<DomainEventListener<*>>,
    ): SpringDomainEventListenerRegistrationMachine {
        return SpringDomainEventListenerRegistrationMachine(
            springDomainEventBus,
            domainEventListeners,
        )
    }
}
