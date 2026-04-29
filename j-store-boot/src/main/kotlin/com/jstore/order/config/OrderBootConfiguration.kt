package com.jstore.com.jstore.order.config

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.SpringDomainEventBus
import com.jstore.common.framework.event.SpringDomainEventDispatcher
import com.jstore.common.framework.event.SpringDomainEventListenerRegistrationMachine
import com.jstore.common.framework.event.SpringDomainEventListenerRegistry
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.service.OrderService
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
    fun springDomainEventListenerRegistry(applicationContext: ConfigurableApplicationContext) : SpringDomainEventListenerRegistry {
        return SpringDomainEventListenerRegistry(applicationContext)
    }

    @Bean
    fun springDomainEventDispatcher(applicationEventPublisher: ApplicationEventPublisher): SpringDomainEventDispatcher {
        return SpringDomainEventDispatcher(applicationEventPublisher)
    }

    @Bean
    fun springDomainEventBus(springDomainEventRegistry: SpringDomainEventListenerRegistry, springDomainEventDispatcher: SpringDomainEventDispatcher): SpringDomainEventBus {
        return SpringDomainEventBus(springDomainEventRegistry, springDomainEventDispatcher)
    }

    @Bean
    fun springDomainEventListenerRegistrationMachine(springDomainEventBus: SpringDomainEventBus, domainEventListeners: List<DomainEventListener<*>>): SpringDomainEventListenerRegistrationMachine {
        return SpringDomainEventListenerRegistrationMachine(springDomainEventBus, domainEventListeners)
    }


}