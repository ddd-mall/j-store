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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.event.OrderCompletedEvent
import com.jstore.outbox.*
import com.jstore.outbox.spring.messaging.TestReserveInventoryCommand
import com.jstore.outbox.spring.persistence.OutboxEntryPOJpaRepository
import com.jstore.outbox.spring.polling.OutboxEntryRepository
import java.time.Instant
import kotlin.test.*
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class OutboxBackendSelectionTest {
    private fun runner() =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration::class.java))
            .withBean(
                ObjectMapper::class.java,
                { ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule()) },
            )
            .withBean(SnowFlakSequence::class.java, { SnowFlakSequence(1, 1) })
            .withPropertyValues("jstore.outbox.enabled=true", "jstore.outbox.mode=recording")

    @Test
    fun `independent backend publishes with no polling database or runtime`() {
        val accepted = mutableListOf<OutboxMessage>()
        val positions = mutableMapOf<OutboxStreamKey, Long>()
        val backend =
            OutboxBackend(
                "recording",
                OutboxWriter { accepted.addAll(it) },
                OutboxStreamSequenceAllocator { transport, key ->
                    val stream = OutboxStreamKey(transport, key)
                    (positions.getOrDefault(stream, 0) + 1).also { positions[stream] = it }
                },
            )
        runner().withBean("recordingBackend", OutboxBackend::class.java, { backend }).run { context
            ->
            assertNull(context.startupFailure)
            assertTrue(context.getBeansOfType(OutboxEntryRepository::class.java).isEmpty())
            assertTrue(context.getBeansOfType(OutboxEntryPOJpaRepository::class.java).isEmpty())
            assertTrue(context.getBeansOfType(OutboxScheduler::class.java).isEmpty())
            assertTrue(context.getBeansOfType(OutboxCleaner::class.java).isEmpty())
            assertTrue(context.getBeansOfType(OutboxMonitor::class.java).isEmpty())
            assertTrue(context.getBeansOfType(OutboxRelaySignal::class.java).isEmpty())
            assertTrue(context.getBeansOfType(DomainEventPublisher::class.java).isNotEmpty())
            val command = TestReserveInventoryCommand(42, Instant.parse("2026-01-01T00:00:00Z"))
            val publisher = context.getBean(IntegrationMessagePublisher::class.java)
            publisher.publish(command)
            publisher.publish(command)
            assertEquals(listOf(1L, 2L), accepted.map { it.sequenceNo })
            assertEquals(listOf(command.messageId, command.messageId), accepted.map { it.eventId })
            assertTrue(
                accepted.all {
                    it.transportId == "local" && it.acceptBefore == command.acceptBefore
                }
            )
            val event = OrderCompletedEvent(OrderId(42), command.occurredAt)
            context.getBean(DomainEventPublisher::class.java).publishEvents(listOf(event))
            val domainMessage = accepted.last()
            assertEquals(event.eventId, domainMessage.eventId)
            assertEquals(OutboxMessageKind.DOMAIN_EVENT, domainMessage.messageKind)
            assertEquals("local-domain", domainMessage.transportId)
            assertEquals(1L, domainMessage.sequenceNo)
        }
    }

    @Test
    fun `missing selected backend fails instead of falling back to polling`() {
        runner().run { assertNotNull(it.startupFailure) }
    }

    @Test
    fun `mismatched backend fails`() {
        runner()
            .withBean(
                OutboxBackend::class.java,
                {
                    OutboxBackend(
                        "different",
                        OutboxWriter {},
                        OutboxStreamSequenceAllocator { _, _ -> 1 },
                    )
                },
            )
            .run { assertNotNull(it.startupFailure) }
    }

    @Test
    fun `duplicate backend owners fail even with the same implementation id`() {
        val backend =
            OutboxBackend("recording", OutboxWriter {}, OutboxStreamSequenceAllocator { _, _ -> 1 })
        runner()
            .withBean("firstBackend", OutboxBackend::class.java, { backend })
            .withBean("secondBackend", OutboxBackend::class.java, { backend })
            .run { assertNotNull(it.startupFailure) }
    }

    @Test
    fun `disabled outbox does not require a backend`() {
        runner().withPropertyValues("jstore.outbox.enabled=false").run { context ->
            assertNull(context.startupFailure)
            assertTrue(context.getBeansOfType(IntegrationMessagePublisher::class.java).isEmpty())
            assertTrue(context.getBeansOfType(OutboxScheduler::class.java).isEmpty())
        }
    }
}
