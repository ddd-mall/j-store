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
package com.jstore.outbox

import com.jstore.messaging.IntegrationMessageMetadata
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Instant

class IntegrationMessagingModelTest :
    FunSpec({
        test("logical destination route selects physical destinations and delivery profiles") {
            val planner =
                IntegrationPublicationPlanner(
                    defaultTargets = listOf("local"),
                    routes =
                        listOf(
                            IntegrationRoute(
                                logicalDestination = "inventory.commands",
                                deliveries =
                                    listOf(
                                        IntegrationDeliveryRoute(
                                            transportId = "kafka",
                                            destination = "commerce.inventory.commands.v1",
                                            deliveryProfile = "CHECKOUT_CRITICAL",
                                        ),
                                        IntegrationDeliveryRoute(
                                            transportId = "local",
                                            destination = "inventory.commands",
                                            deliveryProfile = "CHECKOUT_CRITICAL",
                                        ),
                                    ),
                            )
                        ),
                )

            planner
                .plan("inventory.commands")
                .shouldContainExactly(
                    IntegrationPublication(
                        transportId = "kafka",
                        logicalDestination = "inventory.commands",
                        destination = "commerce.inventory.commands.v1",
                        deliveryProfile = "CHECKOUT_CRITICAL",
                    ),
                    IntegrationPublication(
                        transportId = "local",
                        logicalDestination = "inventory.commands",
                        destination = "inventory.commands",
                        deliveryProfile = "CHECKOUT_CRITICAL",
                    ),
                )
        }

        test("unconfigured logical destination uses globally configured default transports") {
            IntegrationPublicationPlanner(defaultTargets = listOf("local", "kafka"))
                .plan("notification.events")
                .shouldContainExactly(
                    IntegrationPublication(
                        transportId = "kafka",
                        logicalDestination = "notification.events",
                        destination = "notification.events",
                        deliveryProfile = "STANDARD",
                    ),
                    IntegrationPublication(
                        transportId = "local",
                        logicalDestination = "notification.events",
                        destination = "notification.events",
                        deliveryProfile = "STANDARD",
                    ),
                )
        }

        test("duplicate logical routes are rejected") {
            shouldThrow<IllegalArgumentException> {
                IntegrationPublicationPlanner(
                    defaultTargets = listOf("local"),
                    routes =
                        listOf(
                            IntegrationRoute(
                                "inventory.commands",
                                listOf(IntegrationDeliveryRoute("local", "inventory.commands")),
                            ),
                            IntegrationRoute(
                                "inventory.commands",
                                listOf(IntegrationDeliveryRoute("kafka", "inventory.commands")),
                            ),
                        ),
                )
            }
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
            val entry = entry("kafka")
            val router =
                OutboxDeliveryRouter(
                    listOf(
                        recordingChannel("local-domain", calls),
                        recordingChannel("kafka", calls),
                    )
                )

            router.deliver(entry)

            calls shouldBe listOf("kafka:${entry.id}")
        }

        test("outbox router rejects missing target channel") {
            val router = OutboxDeliveryRouter(emptyList())

            shouldThrow<IllegalStateException> {
                router.deliver(entry("kafka"))
            }
        }

        test("outbox router rejects ambiguous target channels") {
            val calls = mutableListOf<String>()
            val router =
                OutboxDeliveryRouter(
                    listOf(
                        recordingChannel("kafka", calls),
                        recordingChannel("kafka", calls),
                    )
                )

            shouldThrow<IllegalStateException> {
                router.deliver(entry("kafka"))
            }
            calls shouldBe emptyList()
        }
    })

private fun recordingChannel(
    transport: String,
    calls: MutableList<String>,
) =
    object : OutboxDeliveryChannel {
        override val transportId: String = transport

        override fun deliver(entry: OutboxEntry) {
            calls += "$transport:${entry.id}"
        }
    }

private fun entry(transportId: String) =
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
        deliveryTarget = OutboxDeliveryTarget.BROKER,
        transportId = transportId,
        orderingKey = "5:Order:1:1",
        sequenceNo = 1,
    )
