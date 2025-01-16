package com.jstore.order.config

import com.jstore.common.framework.DomainEventRegistry
import com.jstore.common.framework.SimpleDomainEventRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
open class OrderBeansConfig {

    @Bean(name = ["businessExecutor"])
    open fun businessExecutor(): ThreadPoolTaskExecutor {
        val executorService = ThreadPoolTaskExecutor()
        executorService.setThreadNamePrefix("domain-event-registry-default-")
        executorService.corePoolSize = 1
        executorService.maxPoolSize = 30
        executorService.queueCapacity = 1000
        executorService.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executorService.initialize()
        return executorService
    }

    @Bean(name = ["domainEventRegistry"])
    open fun domainEventRegistry(businessExecutor : ThreadPoolTaskExecutor): DomainEventRegistry {
        return SimpleDomainEventRegistry(businessExecutor)
    }



}