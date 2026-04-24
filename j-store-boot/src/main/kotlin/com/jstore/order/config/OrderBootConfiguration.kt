package com.jstore.com.jstore.order.config

import com.jstore.common.framework.event.*
import com.jstore.common.persistent.SnowFlakSequence
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
    fun domainEventPublisher(springDomainEventBus: SpringDomainEventBus) : DomainEventPublisher {
        return SpringDomainEventPublisher(springDomainEventBus)
    }

    @Bean
    fun springDomainEventListenerRegistrationMachine(springDomainEventBus: SpringDomainEventBus, domainEventListeners: List<DomainEventListener<*>>): SpringDomainEventListenerRegistrationMachine {
        return SpringDomainEventListenerRegistrationMachine(springDomainEventBus, domainEventListeners)
    }


}