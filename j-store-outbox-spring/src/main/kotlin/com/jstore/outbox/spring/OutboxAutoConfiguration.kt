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
package com.jstore.outbox.spring

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.LocalDomainEventBus
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.messaging.IntegrationMessageHandler
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.messaging.IntegrationMessageTransport
import com.jstore.messaging.LocalIntegrationMessageBus
import com.jstore.messaging.MessageConsumptionRepository
import com.jstore.messaging.local.event.*
import com.jstore.messaging.local.integration.SpringLocalIntegrationMessageBus
import com.jstore.outbox.*
import com.jstore.outbox.spring.messaging.*
import com.jstore.outbox.spring.persistence.*
import io.micrometer.core.instrument.MeterRegistry
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.PlatformTransactionManager

@AutoConfiguration
@Import(OutboxJpaConfiguration::class)
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
    fun messageConsumptionRepository(entityManager: EntityManager): MessageConsumptionRepository {
        return MessageConsumptionRepositoryImpl(entityManager)
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
    fun integrationTransportPlanner(properties: MessagingProperties): IntegrationTransportPlanner =
        IntegrationTransportPlanner(properties.targets)

    @Bean
    fun integrationMessagePublisher(
        outboxEntryRepository: OutboxEntryRepository,
        integrationMessageSerializer: IntegrationMessageSerializer,
        snowFlakSequence: SnowFlakSequence,
        integrationMessageTypeRegistry: IntegrationMessageTypeRegistry,
        integrationTransportPlanner: IntegrationTransportPlanner,
    ): IntegrationMessagePublisher =
        OutboxIntegrationMessagePublisher(
            outboxEntryRepository,
            integrationMessageSerializer,
            snowFlakSequence,
            integrationMessageTypeRegistry,
            integrationTransportPlanner,
        )

    @Bean
    fun springDomainEventListenerRegistry(
        applicationContext: ConfigurableApplicationContext,
        messageConsumptionRepository: MessageConsumptionRepository,
    ): SpringDomainEventListenerRegistry =
        SpringDomainEventListenerRegistry(applicationContext, messageConsumptionRepository)

    @Bean
    fun localDomainEventBus(
        registry: SpringDomainEventListenerRegistry,
        applicationEventPublisher: ApplicationEventPublisher,
    ): LocalDomainEventBus = SpringLocalDomainEventBus(registry, applicationEventPublisher)

    @Bean
    fun springDomainEventListenerRegistrationMachine(
        localDomainEventBus: LocalDomainEventBus,
        listeners: List<DomainEventListener<*>>,
    ): SpringDomainEventListenerRegistrationMachine =
        SpringDomainEventListenerRegistrationMachine(localDomainEventBus, listeners)

    @Bean
    fun localIntegrationMessageBus(
        handlers: List<IntegrationMessageHandler<*>>,
        messageConsumptionRepository: MessageConsumptionRepository,
    ): LocalIntegrationMessageBus =
        SpringLocalIntegrationMessageBus(handlers, messageConsumptionRepository)

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
    fun transportConfigurationGuard(
        properties: MessagingProperties,
        localChannels: List<OutboxDeliveryChannel>,
        transports: ObjectProvider<IntegrationMessageTransport>,
    ): TransportConfigurationGuard =
        TransportConfigurationGuard(properties, localChannels, transports)

    @Bean
    fun localDomainEventDeliveryChannel(
        eventSerializer: EventSerializer,
        localDomainEventBus: LocalDomainEventBus,
    ): OutboxDeliveryChannel = LocalDomainEventDeliveryChannel(eventSerializer, localDomainEventBus)

    @Bean
    fun outboxDeliveryRouter(
        localChannels: List<OutboxDeliveryChannel>,
        transports: ObjectProvider<IntegrationMessageTransport>,
    ): OutboxDeliveryRouter =
        OutboxDeliveryRouter(
            localChannels +
                transports
                    .orderedStream()
                    .map(::TransportIntegrationMessageDeliveryChannel)
                    .toList()
        )

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

@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(OutboxEntryPOJpaRepository::class)
@EntityScan(
    basePackageClasses =
        [
            OutboxEntryPO::class,
            OutboxDeadLetterAuditPO::class,
            DomainEventConsumptionPO::class,
        ]
)
@EnableJpaRepositories(basePackageClasses = [OutboxEntryPOJpaRepository::class])
internal class OutboxJpaConfiguration
