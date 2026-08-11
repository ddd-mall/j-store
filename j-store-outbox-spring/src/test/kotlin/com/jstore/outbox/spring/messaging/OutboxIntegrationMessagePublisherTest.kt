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
package com.jstore.outbox.spring.messaging

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.messaging.IntegrationCommand
import com.jstore.messaging.IntegrationMessageType
import com.jstore.outbox.InMemoryIntegrationMessageTypeRegistry
import com.jstore.outbox.IntegrationMessageSerializer
import com.jstore.outbox.IntegrationTransportPlanner
import com.jstore.outbox.OutboxDeliveryTarget
import com.jstore.outbox.OutboxEntryRepository
import com.jstore.outbox.OutboxMessageKind
import com.jstore.outbox.OutboxStreamSequenceAllocator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class OutboxIntegrationMessagePublisherTest :
    FunSpec({
        test("multiple targets persist independently retryable publications") {
            val repository = mock<OutboxEntryRepository>()
            val serializer = mock<IntegrationMessageSerializer>()
            val sequenceAllocator = mock<OutboxStreamSequenceAllocator>()
            val registry = InMemoryIntegrationMessageTypeRegistry()
            registry.register("test.inventory.reserve", 1, TestReserveInventoryCommand::class.java)
            whenever(serializer.serialize(message)).thenReturn("{\"orderId\":42}")
            val orderingKey = "57d0d731fe2cefe49a264ba7e12c17ae2b8de8fae70f4703504532a64e200f47"
            whenever(sequenceAllocator.nextSequence("local", orderingKey)).thenReturn(7)
            whenever(sequenceAllocator.nextSequence("kafka", orderingKey)).thenReturn(11)
            val publisher =
                OutboxIntegrationMessagePublisher(
                    repository,
                    serializer,
                    SnowFlakSequence(1, 1),
                    registry,
                    IntegrationTransportPlanner(listOf("local", "kafka")),
                    sequenceAllocator,
                )

            publisher.publish(message)

            val captor = argumentCaptor<com.jstore.outbox.OutboxEntry>()
            verify(repository, times(2)).save(captor.capture())
            captor.allValues
                .map { it.deliveryTarget }
                .shouldContainExactly(
                    OutboxDeliveryTarget.BROKER,
                    OutboxDeliveryTarget.LOCAL_INTEGRATION,
                )
            captor.allValues.map { it.transportId }.shouldContainExactly("kafka", "local")
            captor.allValues.map { it.eventId }.distinct() shouldBe listOf(message.messageId)
            captor.allValues.map { it.messageKind }.distinct() shouldBe
                listOf(OutboxMessageKind.INTEGRATION_COMMAND)
            captor.allValues.map { it.partitionKey }.distinct() shouldBe
                listOf(message.partitionKey)
            captor.allValues.map { it.correlationId }.distinct() shouldBe
                listOf(message.correlationId)
            captor.allValues.map { it.orderingKey }.distinct() shouldBe listOf(orderingKey)
            captor.allValues.map { it.sequenceNo }.shouldContainExactly(11, 7)
        }

        test("blank optional metadata is rejected before publication") {
            val repository = mock<OutboxEntryRepository>()
            val serializer = mock<IntegrationMessageSerializer>()
            val sequenceAllocator = mock<OutboxStreamSequenceAllocator>()
            val registry = InMemoryIntegrationMessageTypeRegistry()
            registry.register("test.inventory.reserve", 1, TestReserveInventoryCommand::class.java)
            val publisher =
                OutboxIntegrationMessagePublisher(
                    repository,
                    serializer,
                    SnowFlakSequence(1, 1),
                    registry,
                    IntegrationTransportPlanner(listOf("local")),
                    sequenceAllocator,
                )

            listOf(
                    message.copy(causationId = " "),
                    message.copy(tenantId = " "),
                )
                .forEach { invalidMessage ->
                    shouldThrow<IllegalArgumentException> { publisher.publish(invalidMessage) }
                }

            verifyNoInteractions(repository, serializer, sequenceAllocator)
        }
    })

@IntegrationMessageType(name = "test.inventory.reserve", version = 1)
data class TestReserveInventoryCommand(
    val orderId: Long,
    override val occurredAt: Instant,
    override val causationId: String = "order-created-42",
    override val tenantId: String = "merchant-7",
) : IntegrationCommand {
    override val messageId: String = "message-1"
    override val messageName: String = "test.inventory.reserve"
    override val messageVersion: Int = 1
    override val partitionKey: String = orderId.toString()
    override val correlationId: String = "checkout-42"
    override val destination: String = "inventory.commands"
}

val message =
    TestReserveInventoryCommand(
        orderId = 42,
        occurredAt = Instant.parse("2026-08-05T00:00:00Z"),
    )
