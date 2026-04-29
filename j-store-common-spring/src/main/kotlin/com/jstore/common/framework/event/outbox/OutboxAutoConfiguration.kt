package com.jstore.common.framework.event.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.common.framework.event.DomainEventBus
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.outbox.persistence.OutboxEntryPOJpaRepository
import com.jstore.common.framework.event.outbox.persistence.OutboxEntryRepositoryImpl
import com.jstore.common.persistent.SnowFlakSequence
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@Configuration
@EnableConfigurationProperties(OutboxProperties::class)
@ConditionalOnProperty(prefix = "jstore.outbox", name = ["enabled"], havingValue = "true")
@EnableScheduling
class OutboxAutoConfiguration {

    @Bean
    fun eventSerializer(objectMapper: ObjectMapper): EventSerializer {
        return JacksonEventSerializer(objectMapper)
    }

    @Bean
    fun outboxEntryRepository(jpaRepository: OutboxEntryPOJpaRepository): OutboxEntryRepository {
        return OutboxEntryRepositoryImpl(jpaRepository)
    }

    @Bean
    fun domainEventPublisher(
        outboxEntryRepository: OutboxEntryRepository,
        eventSerializer: EventSerializer,
        snowFlakSequence: SnowFlakSequence,
    ): DomainEventPublisher {
        return OutboxEventPublisher(outboxEntryRepository, eventSerializer, snowFlakSequence)
    }

    @Bean
    fun outboxPublisher(
        outboxEntryRepository: OutboxEntryRepository,
        eventSerializer: EventSerializer,
        domainEventBus: DomainEventBus,
        properties: OutboxProperties,
    ): OutboxPublisher {
        return OutboxPublisher(outboxEntryRepository, eventSerializer, domainEventBus, properties)
    }

    @Bean
    fun outboxCleaner(
        outboxEntryRepository: OutboxEntryRepository,
        properties: OutboxProperties,
    ): OutboxCleaner {
        return OutboxCleaner(outboxEntryRepository, properties)
    }

    @Bean
    fun outboxScheduler(
        outboxPublisher: OutboxPublisher,
        outboxCleaner: OutboxCleaner,
    ): OutboxScheduler {
        return OutboxScheduler(outboxPublisher, outboxCleaner)
    }
}
