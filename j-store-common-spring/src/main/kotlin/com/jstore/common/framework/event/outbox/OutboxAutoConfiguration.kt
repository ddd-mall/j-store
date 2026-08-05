package com.jstore.common.framework.event.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.common.framework.event.DomainEventConsumptionRepository
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.LocalDomainEventBus
import com.jstore.common.framework.event.SpringDomainEventMulticasterGuard
import com.jstore.common.framework.event.outbox.persistence.OutboxEntryPOJpaRepository
import com.jstore.common.framework.event.outbox.persistence.OutboxEntryRepositoryImpl
import com.jstore.common.framework.event.persistence.DomainEventConsumptionRepositoryImpl
import com.jstore.common.framework.messaging.BrokerIntegrationMessageDeliveryChannel
import com.jstore.common.framework.messaging.BrokerIntegrationMessageTransport
import com.jstore.common.framework.messaging.BrokerTransportModeGuard
import com.jstore.common.framework.messaging.IntegrationMessageHandler
import com.jstore.common.framework.messaging.IntegrationMessagePublisher
import com.jstore.common.framework.messaging.IntegrationPublicationPlanner
import com.jstore.common.framework.messaging.JacksonIntegrationMessageSerializer
import com.jstore.common.framework.messaging.LocalIntegrationMessageBus
import com.jstore.common.framework.messaging.LocalIntegrationMessageDeliveryChannel
import com.jstore.common.framework.messaging.MessagingProperties
import com.jstore.common.framework.messaging.OutboxIntegrationMessagePublisher
import com.jstore.common.framework.messaging.SpringIntegrationMessageTypeRegistryRegistrar
import com.jstore.common.framework.messaging.SpringLocalIntegrationMessageBus
import com.jstore.common.persistent.SnowFlakSequence
import io.micrometer.core.instrument.MeterRegistry
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.PlatformTransactionManager

@Configuration
@EnableConfigurationProperties(
    OutboxProperties::class,
    OutboxObservabilityProperties::class,
    MessagingProperties::class,
)
@ConditionalOnProperty(prefix = "jstore.outbox", name = ["enabled"], havingValue = "true")
@EnableScheduling
class OutboxAutoConfiguration {

    @Bean
    fun eventTypeRegistry(): EventTypeRegistry {
        return InMemoryEventTypeRegistry()
    }

    @Bean
    fun springEventTypeRegistryRegistrar(
        eventTypeRegistry: EventTypeRegistry,
        properties: OutboxProperties,
    ): SpringEventTypeRegistryRegistrar {
        return SpringEventTypeRegistryRegistrar(eventTypeRegistry, properties.eventTypeScanPackages)
    }

    @Bean
    fun eventUpcasterRegistry(upcasters: ObjectProvider<EventUpcaster>): EventUpcasterRegistry {
        return InMemoryEventUpcasterRegistry(upcasters)
    }

    @Bean
    fun integrationMessageTypeRegistry(): IntegrationMessageTypeRegistry =
        InMemoryIntegrationMessageTypeRegistry()

    @Bean
    fun springIntegrationMessageTypeRegistryRegistrar(
        registry: IntegrationMessageTypeRegistry,
        properties: OutboxProperties,
    ): SpringIntegrationMessageTypeRegistryRegistrar =
        SpringIntegrationMessageTypeRegistryRegistrar(registry, properties.eventTypeScanPackages)

    @Bean
    fun integrationMessageSerializer(
        objectMapper: ObjectMapper,
        registry: IntegrationMessageTypeRegistry,
    ): IntegrationMessageSerializer = JacksonIntegrationMessageSerializer(objectMapper, registry)

    @Bean
    fun eventSerializer(
        objectMapper: ObjectMapper,
        eventTypeRegistry: EventTypeRegistry,
        eventUpcasterRegistry: EventUpcasterRegistry,
    ): EventSerializer {
        return JacksonEventSerializer(objectMapper, eventTypeRegistry, eventUpcasterRegistry)
    }

    @Bean
    fun outboxEntryRepository(
        jpaRepository: OutboxEntryPOJpaRepository,
        entityManager: EntityManager,
    ): OutboxEntryRepository {
        return OutboxEntryRepositoryImpl(jpaRepository, entityManager)
    }

    @Bean
    fun domainEventConsumptionRepository(
        entityManager: EntityManager
    ): DomainEventConsumptionRepository {
        return DomainEventConsumptionRepositoryImpl(entityManager)
    }

    @Bean
    fun domainEventPublisher(
        outboxEntryRepository: OutboxEntryRepository,
        eventSerializer: EventSerializer,
        snowFlakSequence: SnowFlakSequence,
        eventTypeRegistry: EventTypeRegistry,
    ): DomainEventPublisher {
        return OutboxEventPublisher(
            outboxEntryRepository,
            eventSerializer,
            snowFlakSequence,
            eventTypeRegistry,
        )
    }

    @Bean
    fun integrationPublicationPlanner(
        properties: MessagingProperties
    ): IntegrationPublicationPlanner = IntegrationPublicationPlanner(properties.mode)

    @Bean
    fun integrationMessagePublisher(
        outboxEntryRepository: OutboxEntryRepository,
        integrationMessageSerializer: IntegrationMessageSerializer,
        snowFlakSequence: SnowFlakSequence,
        integrationMessageTypeRegistry: IntegrationMessageTypeRegistry,
        integrationPublicationPlanner: IntegrationPublicationPlanner,
    ): IntegrationMessagePublisher =
        OutboxIntegrationMessagePublisher(
            outboxEntryRepository,
            integrationMessageSerializer,
            snowFlakSequence,
            integrationMessageTypeRegistry,
            integrationPublicationPlanner,
        )

    @Bean
    fun localIntegrationMessageBus(
        handlers: List<IntegrationMessageHandler<*>>,
        domainEventConsumptionRepository: DomainEventConsumptionRepository,
    ): LocalIntegrationMessageBus =
        SpringLocalIntegrationMessageBus(handlers, domainEventConsumptionRepository)

    @Bean
    fun localIntegrationMessageDeliveryChannel(
        integrationMessageSerializer: IntegrationMessageSerializer,
        localIntegrationMessageBus: LocalIntegrationMessageBus,
    ): OutboxDeliveryChannel =
        LocalIntegrationMessageDeliveryChannel(
            integrationMessageSerializer,
            localIntegrationMessageBus,
        )

    @Bean
    @ConditionalOnBean(BrokerIntegrationMessageTransport::class)
    fun brokerIntegrationMessageDeliveryChannel(
        transport: BrokerIntegrationMessageTransport
    ): OutboxDeliveryChannel = BrokerIntegrationMessageDeliveryChannel(transport)

    @Bean
    fun brokerTransportModeGuard(
        properties: MessagingProperties,
        transports: ObjectProvider<BrokerIntegrationMessageTransport>,
    ): BrokerTransportModeGuard = BrokerTransportModeGuard(properties, transports)

    @Bean
    fun localDomainEventDeliveryChannel(
        eventSerializer: EventSerializer,
        localDomainEventBus: LocalDomainEventBus,
    ): OutboxDeliveryChannel = LocalDomainEventDeliveryChannel(eventSerializer, localDomainEventBus)

    @Bean
    fun outboxDeliveryRouter(channels: List<OutboxDeliveryChannel>): OutboxDeliveryRouter =
        OutboxDeliveryRouter(channels)

    @Bean
    fun outboxPublisher(
        outboxEntryRepository: OutboxEntryRepository,
        deliveryRouter: OutboxDeliveryRouter,
        properties: OutboxProperties,
        outboxMonitor: OutboxMonitor,
        transactionOperations: OutboxRelayTransactionOperations,
    ): OutboxPublisher {
        return OutboxPublisher(
            outboxEntryRepository,
            deliveryRouter,
            properties,
            outboxMonitor,
            transactionOperations,
        )
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
        outboxMonitor: OutboxMonitor,
        schedulerExecutionState: SchedulerExecutionState,
    ): OutboxScheduler {
        return OutboxScheduler(
            outboxPublisher,
            outboxCleaner,
            outboxMonitor,
            schedulerState = schedulerExecutionState,
        )
    }

    @Bean fun schedulerExecutionState(): SchedulerExecutionState = SchedulerExecutionState()

    @Bean
    fun outboxOperationalHealth(
        outboxEntryRepository: OutboxEntryRepository,
        schedulerExecutionState: SchedulerExecutionState,
        observabilityProperties: OutboxObservabilityProperties,
        properties: OutboxProperties,
    ): OutboxOperationalHealth =
        OutboxOperationalHealth(
            outboxEntryRepository,
            schedulerExecutionState,
            observabilityProperties,
            properties.maxRetryCount,
        )

    @Bean
    fun outboxMonitor(
        meterRegistryProvider: ObjectProvider<MeterRegistry>,
        outboxEntryRepository: OutboxEntryRepository,
        outboxOperationalHealth: OutboxOperationalHealth,
        schedulerExecutionState: SchedulerExecutionState,
    ): OutboxMonitor {
        val meterRegistry = meterRegistryProvider.getIfAvailable() ?: return NoopOutboxMonitor
        return MicrometerOutboxMonitor(
            meterRegistry,
            outboxEntryRepository,
            outboxOperationalHealth,
            schedulerExecutionState,
        )
    }

    @Bean
    fun outboxRelayTransactionOperations(
        transactionManager: PlatformTransactionManager
    ): OutboxRelayTransactionOperations {
        return SpringOutboxRelayTransactionOperations(transactionManager)
    }

    @Bean
    fun springDomainEventMulticasterGuard(
        applicationContext: ApplicationContext,
        properties: OutboxProperties,
    ): SpringDomainEventMulticasterGuard {
        return SpringDomainEventMulticasterGuard(
            applicationContext,
            properties.asyncMulticasterFailFast,
        )
    }

    @Bean
    fun outboxDeadLetterService(
        outboxEntryRepository: OutboxEntryRepository,
        outboxMonitor: OutboxMonitor,
    ): OutboxDeadLetterService {
        return OutboxDeadLetterService(outboxEntryRepository, outboxMonitor)
    }
}
