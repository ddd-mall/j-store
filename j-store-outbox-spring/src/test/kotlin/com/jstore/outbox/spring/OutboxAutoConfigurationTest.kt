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
import com.jstore.messaging.IntegrationMessageEnvelope
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.messaging.IntegrationMessageTransport
import com.jstore.outbox.OutboxDeliveryChannel
import com.jstore.outbox.spring.persistence.OutboxEntryPOJpaRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import jakarta.persistence.EntityManager
import org.mockito.kotlin.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * OutboxAutoConfiguration 集成测试
 *
 * 验证功能开关（jstore.outbox.enabled）控制 Bean 注册行为。
 *
 * Validates: Requirements 5.2, 5.4, 5.5
 */
class OutboxAutoConfigurationTest :
    FunSpec({
        val contextRunner =
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration::class.java))
                .withUserConfiguration(TestInfrastructureConfig::class.java)

        test("enabled=true registers OutboxEventPublisher as DomainEventPublisher") {
            contextRunner.withPropertyValues("jstore.outbox.enabled=true").run { context ->
                context.containsBean("domainEventPublisher") shouldBe true
                context
                    .getBean(DomainEventPublisher::class.java)
                    .shouldBeInstanceOf<OutboxEventPublisher>()
                context.containsBean("outboxPublisher") shouldBe true
                context.containsBean("outboxCleaner") shouldBe true
                context.containsBean("eventSerializer") shouldBe true
                context.containsBean("eventTypeRegistry") shouldBe true
                context.containsBean("springEventTypeRegistryRegistrar") shouldBe true
                context.containsBean("outboxEntryRepository") shouldBe true
                context.containsBean("messageConsumptionRepository") shouldBe true
                context.containsBean("outboxRelayTransactionOperations") shouldBe true
                context.containsBean("springDomainEventMulticasterGuard") shouldBe true
                context.getBean(IntegrationMessagePublisher::class.java).shouldNotBeNull()
                context.getBeansOfType(OutboxDeliveryChannel::class.java).keys shouldBe
                    setOf(
                        "localDomainEventDeliveryChannel",
                        "localIntegrationMessageDeliveryChannel",
                    )
            }
        }

        test("configured external target fails fast when its transport is absent") {
            contextRunner
                .withPropertyValues(
                    "jstore.outbox.enabled=true",
                    "jstore.messaging.targets=kafka",
                )
                .run { context -> context.startupFailure.shouldNotBeNull() }
        }

        test("configured external target starts when matching transport is provided") {
            contextRunner
                .withUserConfiguration(BrokerTransportConfig::class.java)
                .withPropertyValues(
                    "jstore.outbox.enabled=true",
                    "jstore.messaging.targets=kafka",
                )
                .run { context ->
                    context.startupFailure shouldBe null
                    context.containsBean("transportConfigurationGuard") shouldBe true
                }
        }

        test("transport adapter cannot claim the reserved local-domain transport ID") {
            contextRunner
                .withUserConfiguration(ReservedDomainTransportConfig::class.java)
                .withPropertyValues("jstore.outbox.enabled=true")
                .run { context -> context.startupFailure.shouldNotBeNull() }
        }

        test("enabled=false does not register OutboxAutoConfiguration beans") {
            contextRunner.withPropertyValues("jstore.outbox.enabled=false").run { context ->
                context.containsBean("domainEventPublisher") shouldBe false
                context.containsBean("outboxPublisher") shouldBe false
                context.containsBean("outboxCleaner") shouldBe false
            }
        }

        test("no property configured does not register OutboxAutoConfiguration beans") {
            contextRunner.run { context ->
                context.containsBean("domainEventPublisher") shouldBe false
                context.containsBean("outboxPublisher") shouldBe false
                context.containsBean("outboxCleaner") shouldBe false
            }
        }
    }) {
    /** 提供 OutboxAutoConfiguration 所需的基础设施 Bean（mock 实现）。 */
    @Configuration
    class TestInfrastructureConfig {
        @Bean fun objectMapper(): ObjectMapper = ObjectMapper()

        @Bean fun outboxEntryPOJpaRepository(): OutboxEntryPOJpaRepository = mock()

        @Bean fun entityManager(): EntityManager = mock()

        @Bean fun snowFlakSequence(): SnowFlakSequence = SnowFlakSequence(1, 1)

        @Bean fun transactionManager(): PlatformTransactionManager = mock()
    }

    @Configuration
    class BrokerTransportConfig {
        @Bean
        fun kafkaIntegrationMessageTransport(): IntegrationMessageTransport =
            object : IntegrationMessageTransport {
                override val transportId: String = "kafka"

                override fun publish(envelope: IntegrationMessageEnvelope) = Unit
            }
    }

    @Configuration
    class ReservedDomainTransportConfig {
        @Bean
        fun reservedDomainIntegrationMessageTransport(): IntegrationMessageTransport =
            object : IntegrationMessageTransport {
                override val transportId: String = "local-domain"

                override fun publish(envelope: IntegrationMessageEnvelope) = Unit
            }
    }
}
