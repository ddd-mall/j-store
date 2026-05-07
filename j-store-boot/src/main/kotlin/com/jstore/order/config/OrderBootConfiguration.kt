package com.jstore.com.jstore.order.config

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.event.DomainEventConsumptionRepository
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.NoopDomainEventConsumptionRepository
import com.jstore.common.framework.event.SpringDomainEventBus
import com.jstore.common.framework.event.SpringDomainEventListenerRegistrationMachine
import com.jstore.common.framework.event.SpringDomainEventListenerRegistry
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.domain.order.OrderFactory
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
    fun orderService(
        orderFactory: OrderFactory,
        orderRepository: OrderRepository,
        domainEventPublisher: DomainEventPublisher,
    ): OrderService {
        return OrderService(orderFactory,
                orderRepository,
                domainEventPublisher)
    }

    @Bean
    fun springDomainEventListenerRegistry(
        applicationContext: ConfigurableApplicationContext,
        consumptionRepositoryProvider: ObjectProvider<DomainEventConsumptionRepository>,
    ) : SpringDomainEventListenerRegistry {
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
    fun springDomainEventListenerRegistrationMachine(springDomainEventBus: SpringDomainEventBus, domainEventListeners: List<DomainEventListener<*>>): SpringDomainEventListenerRegistrationMachine {
        return SpringDomainEventListenerRegistrationMachine(springDomainEventBus, domainEventListeners)
    }


}
