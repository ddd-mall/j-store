package com.jstore.com.jstore.order.config

import com.jstore.com.jstore.order.framework.SpringDomainEventPublisher
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class OrderBootConfiguration {
    @Bean
    fun snowFlakSequence(): SnowFlakSequence {
        return SnowFlakSequence()
    }

    @Bean
    fun domainEventPublisher() : DomainEventPublisher {
        return SpringDomainEventPublisher()
    }

}