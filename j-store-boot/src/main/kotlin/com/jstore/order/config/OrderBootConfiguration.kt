package com.jstore.com.jstore.order.config

import com.jstore.common.framework.event.*
import com.jstore.common.geo.GeoAddressService
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.goods.api.GoodsSnapshotQueryService
import com.jstore.order.acl.GoodsService
import com.jstore.order.acl.GoodsServiceImpl
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderFactoryImpl
import com.jstore.order.domain.order.OrderRepository
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
    fun goodsService(
        goodsSnapshotQueryService: GoodsSnapshotQueryService
    ): GoodsService {
        return GoodsServiceImpl(
            goodsSnapshotQueryService
        )
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
            geoAddressService
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
            domainEventPublisher
        )
    }

    @Bean
    fun springDomainEventListenerRegistry(
        applicationContext: ConfigurableApplicationContext,
        consumptionRepositoryProvider: ObjectProvider<DomainEventConsumptionRepository>,
    ): SpringDomainEventListenerRegistry {
        return SpringDomainEventListenerRegistry(
            applicationContext,
            consumptionRepositoryProvider.getIfAvailable() ?: NoopDomainEventConsumptionRepository
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
        domainEventListeners: List<DomainEventListener<*>>
    ): SpringDomainEventListenerRegistrationMachine {
        return SpringDomainEventListenerRegistrationMachine(springDomainEventBus, domainEventListeners)
    }


}
