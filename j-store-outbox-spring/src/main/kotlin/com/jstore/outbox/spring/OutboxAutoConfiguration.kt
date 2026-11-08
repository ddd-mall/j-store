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
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.outbox.*
import com.jstore.outbox.spring.messaging.*
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Lazy

@AutoConfiguration
@Import(PollingOutboxConfiguration::class)
@EnableConfigurationProperties(OutboxPublicationProperties::class, MessagingProperties::class)
@ConditionalOnProperty(prefix = "jstore.outbox", name = ["enabled"], havingValue = "true")
class OutboxAutoConfiguration {
  @Bean
  @Lazy(false)
  fun outboxBackendSelection(
      properties: OutboxPublicationProperties,
      backends: List<OutboxBackend>,
  ): OutboxBackendSelection {
    check(backends.size == 1 && backends.single().id == properties.mode) {
      "Expected exactly one Outbox backend for mode=${properties.mode}, found=${backends.map { it.id }}"
    }
    return OutboxBackendSelection(backends.single())
  }

  @Bean
  fun eventTypeRegistry(): EventTypeRegistry {
    return InMemoryEventTypeRegistry()
  }

  @Bean
  fun springEventTypeRegistryRegistrar(
      eventTypeRegistry: EventTypeRegistry,
      properties: OutboxPublicationProperties,
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
      properties: OutboxPublicationProperties,
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
  fun domainEventPublisher(
      selection: OutboxBackendSelection,
      eventSerializer: EventSerializer,
      snowFlakSequence: SnowFlakSequence,
      eventTypeRegistry: EventTypeRegistry,
  ): DomainEventPublisher {
    return OutboxEventPublisher(
        selection.backend.writer,
        eventSerializer,
        snowFlakSequence,
        eventTypeRegistry,
        selection.backend.sequenceAllocator,
    )
  }

  @Bean
  fun integrationPublicationPlanner(
      properties: MessagingProperties
  ): IntegrationPublicationPlanner =
      IntegrationPublicationPlanner(properties.targets, properties.integrationRoutes())

  @Bean
  fun integrationMessagePublisher(
      selection: OutboxBackendSelection,
      integrationMessageSerializer: IntegrationMessageSerializer,
      snowFlakSequence: SnowFlakSequence,
      integrationMessageTypeRegistry: IntegrationMessageTypeRegistry,
      integrationPublicationPlanner: IntegrationPublicationPlanner,
  ): IntegrationMessagePublisher =
      OutboxIntegrationMessagePublisher(
          selection.backend.writer,
          integrationMessageSerializer,
          snowFlakSequence,
          integrationMessageTypeRegistry,
          integrationPublicationPlanner,
          selection.backend.sequenceAllocator,
      )
}
