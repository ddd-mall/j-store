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
package com.jstore.common.framework.messaging

import com.jstore.common.framework.event.outbox.OutboxDeliveryChannel
import com.jstore.common.framework.event.outbox.OutboxDeliveryRouter
import com.jstore.common.framework.event.outbox.OutboxDeliveryTarget
import com.jstore.common.framework.event.outbox.OutboxEntry
import com.jstore.common.framework.event.outbox.OutboxEntryStatus
import com.jstore.common.framework.event.outbox.OutboxMessageKind
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Instant

class IntegrationMessagingModelTest :
    FunSpec({
        test("local mode plans one independently tracked local integration delivery") {
            IntegrationPublicationPlanner(IntegrationMessagingMode.LOCAL)
                .targets()
                .shouldContainExactly(OutboxDeliveryTarget.LOCAL_INTEGRATION)
        }

        test("broker mode plans one broker delivery") {
            IntegrationPublicationPlanner(IntegrationMessagingMode.BROKER)
                .targets()
                .shouldContainExactly(OutboxDeliveryTarget.BROKER)
        }

        test("hybrid mode plans independent local and broker deliveries") {
            IntegrationPublicationPlanner(IntegrationMessagingMode.HYBRID)
                .targets()
                .shouldContainExactly(
                    OutboxDeliveryTarget.LOCAL_INTEGRATION,
                    OutboxDeliveryTarget.BROKER,
                )
        }

        test("integration metadata rejects unstable routing and identity fields") {
            shouldThrow<IllegalArgumentException> {
                IntegrationMessageMetadata(
                    messageId = " ",
                    messageName = "order.created",
                    messageVersion = 1,
                    occurredAt = Instant.parse("2026-08-05T00:00:00Z"),
                    partitionKey = "order-1",
                    correlationId = "checkout-1",
                )
            }
            shouldThrow<IllegalArgumentException> {
                IntegrationMessageMetadata(
                    messageId = "message-1",
                    messageName = "order.created",
                    messageVersion = 0,
                    occurredAt = Instant.parse("2026-08-05T00:00:00Z"),
                    partitionKey = "order-1",
                    correlationId = "checkout-1",
                )
            }
            shouldThrow<IllegalArgumentException> {
                IntegrationMessageMetadata(
                    messageId = "message-1",
                    messageName = "order.created",
                    messageVersion = 1,
                    occurredAt = Instant.parse("2026-08-05T00:00:00Z"),
                    partitionKey = " ",
                    correlationId = "checkout-1",
                )
            }
        }

        test("outbox router delegates to exactly one target channel") {
            val calls = mutableListOf<String>()
            val entry = entry(OutboxDeliveryTarget.BROKER)
            val router =
                OutboxDeliveryRouter(
                    listOf(
                        recordingChannel(OutboxDeliveryTarget.LOCAL_DOMAIN, calls),
                        recordingChannel(OutboxDeliveryTarget.BROKER, calls),
                    )
                )

            router.deliver(entry)

            calls shouldBe listOf("BROKER:${entry.id}")
        }

        test("outbox router rejects missing target channel") {
            val router = OutboxDeliveryRouter(emptyList())

            shouldThrow<IllegalStateException> {
                router.deliver(entry(OutboxDeliveryTarget.BROKER))
            }
        }

        test("outbox router rejects ambiguous target channels") {
            val calls = mutableListOf<String>()
            val router =
                OutboxDeliveryRouter(
                    listOf(
                        recordingChannel(OutboxDeliveryTarget.BROKER, calls),
                        recordingChannel(OutboxDeliveryTarget.BROKER, calls),
                    )
                )

            shouldThrow<IllegalStateException> {
                router.deliver(entry(OutboxDeliveryTarget.BROKER))
            }
            calls shouldBe emptyList()
        }
    })

private fun recordingChannel(
    target: OutboxDeliveryTarget,
    calls: MutableList<String>,
) =
    object : OutboxDeliveryChannel {
        override val target: OutboxDeliveryTarget = target

        override fun deliver(entry: OutboxEntry) {
            calls += "${target.name}:${entry.id}"
        }
    }

private fun entry(target: OutboxDeliveryTarget) =
    OutboxEntry(
        id = "entry-1",
        eventType = "order.created",
        payload = "{}",
        aggregateType = "Order",
        aggregateId = "1",
        status = OutboxEntryStatus.PENDING,
        createdAt = Instant.parse("2026-08-05T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-05T00:00:00Z"),
        messageKind = OutboxMessageKind.INTEGRATION_EVENT,
        deliveryTarget = target,
    )
