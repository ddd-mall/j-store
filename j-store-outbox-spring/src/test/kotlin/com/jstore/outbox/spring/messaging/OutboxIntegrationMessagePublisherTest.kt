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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OutboxIntegrationMessagePublisherTest :
    FunSpec({
        test("multiple targets persist independently retryable publications") {
            val repository = mock<OutboxEntryRepository>()
            val serializer = mock<IntegrationMessageSerializer>()
            val registry = InMemoryIntegrationMessageTypeRegistry()
            registry.register("test.inventory.reserve", 1, TestReserveInventoryCommand::class.java)
            whenever(serializer.serialize(message)).thenReturn("{\"orderId\":42}")
            val publisher =
                OutboxIntegrationMessagePublisher(
                    repository,
                    serializer,
                    SnowFlakSequence(1, 1),
                    registry,
                    IntegrationTransportPlanner(listOf("local", "kafka")),
                )

            publisher.publish(message)

            val captor = argumentCaptor<com.jstore.outbox.OutboxEntry>()
            verify(repository, times(2)).save(captor.capture())
            captor.allValues
                .map { it.deliveryTarget }
                .shouldContainExactly(
                    OutboxDeliveryTarget.LOCAL_INTEGRATION,
                    OutboxDeliveryTarget.BROKER,
                )
            captor.allValues.map { it.transportId }.shouldContainExactly("local", "kafka")
            captor.allValues.map { it.eventId }.distinct() shouldBe listOf(message.messageId)
            captor.allValues.map { it.messageKind }.distinct() shouldBe
                listOf(OutboxMessageKind.INTEGRATION_COMMAND)
            captor.allValues.map { it.partitionKey }.distinct() shouldBe
                listOf(message.partitionKey)
            captor.allValues.map { it.correlationId }.distinct() shouldBe
                listOf(message.correlationId)
        }
    })

@IntegrationMessageType(name = "test.inventory.reserve", version = 1)
data class TestReserveInventoryCommand(
    val orderId: Long,
    override val occurredAt: Instant,
) : IntegrationCommand {
    override val messageId: String = "message-1"
    override val messageName: String = "test.inventory.reserve"
    override val messageVersion: Int = 1
    override val partitionKey: String = orderId.toString()
    override val correlationId: String = "checkout-42"
    override val causationId: String = "order-created-42"
    override val tenantId: String = "merchant-7"
    override val destination: String = "inventory.commands"
}

val message =
    TestReserveInventoryCommand(
        orderId = 42,
        occurredAt = Instant.parse("2026-08-05T00:00:00Z"),
    )
