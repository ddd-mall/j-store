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
package com.jstore.messaging.local.integration

import com.jstore.messaging.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.MDC

class SpringLocalIntegrationMessageBusTest :
    FunSpec({
        test("local bus invokes a command handler once after claiming message id") {
            val handled = mutableListOf<Long>()
            val handler =
                object : IntegrationMessageHandler<TestReserveInventoryCommand> {
                    override fun handlerId() = "inventory.reserve.v1"

                    override fun handle(message: TestReserveInventoryCommand) {
                        handled += message.orderId
                    }
                }
            val consumption = mock<MessageConsumptionRepository>()
            whenever(
                    consumption.tryStart(
                        handler.handlerId(),
                        message.messageId,
                        message.messageName,
                        message.messageVersion,
                    )
                )
                .thenReturn(true, false)
            val bus = SpringLocalIntegrationMessageBus(listOf(handler), consumption)

            bus.publish(message)
            bus.publish(message)

            handled shouldBe listOf(42L)
            verify(consumption, org.mockito.kotlin.times(2))
                .tryStart(
                    handler.handlerId(),
                    message.messageId,
                    message.messageName,
                    message.messageVersion,
                )
        }

        test("command delivery requires exactly one owning handler") {
            val bus =
                SpringLocalIntegrationMessageBus(
                    emptyList(),
                    mock<MessageConsumptionRepository>(),
                )

            shouldThrow<IllegalStateException> { bus.publish(message) }
        }

        test("ordered delivery advances one stream cursor before handler idempotency") {
            val handler =
                object : IntegrationMessageHandler<TestReserveInventoryCommand> {
                    override fun handlerId() = "inventory.reserve.v1"

                    override fun handle(message: TestReserveInventoryCommand) {}
                }
            val consumption = mock<MessageConsumptionRepository>()
            val deliveryOrder = MessageDeliveryOrder("local", "stream-42", 2)
            whenever(
                    consumption.tryStartOrdered(
                        "jstore.local-integration-bus",
                        message.messageId,
                        message.messageName,
                        message.messageVersion,
                        deliveryOrder,
                    )
                )
                .thenReturn(true)
            whenever(
                    consumption.tryStart(
                        handler.handlerId(),
                        message.messageId,
                        message.messageName,
                        message.messageVersion,
                    )
                )
                .thenReturn(true)

            SpringLocalIntegrationMessageBus(listOf(handler), consumption)
                .publish(message, deliveryOrder)

            verify(consumption)
                .tryStartOrdered(
                    "jstore.local-integration-bus",
                    message.messageId,
                    message.messageName,
                    message.messageVersion,
                    deliveryOrder,
                )
            verify(consumption)
                .tryStart(
                    handler.handlerId(),
                    message.messageId,
                    message.messageName,
                    message.messageVersion,
                )
        }

        test("handler receives message logging context and it is cleared between messages") {
            val observed = mutableListOf<Map<String, String?>>()
            val handler =
                object : IntegrationMessageHandler<TestReserveInventoryCommand> {
                    override fun handlerId() = "inventory.reserve.v1"

                    override fun handle(message: TestReserveInventoryCommand) {
                        observed +=
                            mapOf(
                                "message_id" to MDC.get("message_id"),
                                "correlation_id" to MDC.get("correlation_id"),
                                "causation_id" to MDC.get("causation_id"),
                                "transport_id" to MDC.get("transport_id"),
                            )
                    }
                }
            val consumption = mock<MessageConsumptionRepository>()
            whenever(
                    consumption.tryStart(
                        org.mockito.kotlin.any(),
                        org.mockito.kotlin.any(),
                        org.mockito.kotlin.any(),
                        org.mockito.kotlin.any(),
                    )
                )
                .thenReturn(true)
            val bus = SpringLocalIntegrationMessageBus(listOf(handler), consumption)

            bus.publish(message)
            bus.publish(message.copy(orderId = 43).withMessageId("message-2"))

            observed shouldBe
                listOf(
                    mapOf(
                        "message_id" to "message-1",
                        "correlation_id" to "checkout-42",
                        "causation_id" to "order-created-42",
                        "transport_id" to "local",
                    ),
                    mapOf(
                        "message_id" to "message-2",
                        "correlation_id" to "checkout-42",
                        "causation_id" to "order-created-42",
                        "transport_id" to "local",
                    ),
                )
            MDC.get("message_id") shouldBe null
            MDC.get("correlation_id") shouldBe null
            MDC.get("causation_id") shouldBe null
            MDC.get("transport_id") shouldBe null
        }

        test("handler failure does not leak message logging context") {
            val handler =
                object : IntegrationMessageHandler<TestReserveInventoryCommand> {
                    override fun handlerId() = "inventory.reserve.v1"

                    override fun handle(message: TestReserveInventoryCommand) {
                        throw IllegalStateException("synthetic failure")
                    }
                }
            val consumption = mock<MessageConsumptionRepository>()
            whenever(
                    consumption.tryStart(
                        handler.handlerId(),
                        message.messageId,
                        message.messageName,
                        message.messageVersion,
                    )
                )
                .thenReturn(true)

            shouldThrow<IllegalStateException> {
                SpringLocalIntegrationMessageBus(listOf(handler), consumption).publish(message)
            }

            MDC.get("message_id") shouldBe null
            MDC.get("correlation_id") shouldBe null
            MDC.get("causation_id") shouldBe null
            MDC.get("transport_id") shouldBe null
        }

        test("message claim executes inside logging context and failure restores caller context") {
            val handler =
                object : IntegrationMessageHandler<TestReserveInventoryCommand> {
                    override fun handlerId() = "inventory.reserve.v1"

                    override fun handle(message: TestReserveInventoryCommand) = Unit
                }
            val observed = mutableMapOf<String, String?>()
            val consumption = mock<MessageConsumptionRepository>()
            whenever(
                    consumption.tryStart(
                        handler.handlerId(),
                        message.messageId,
                        message.messageName,
                        message.messageVersion,
                    )
                )
                .thenAnswer {
                    observed["message_id"] = MDC.get("message_id")
                    observed["correlation_id"] = MDC.get("correlation_id")
                    observed["causation_id"] = MDC.get("causation_id")
                    observed["transport_id"] = MDC.get("transport_id")
                    throw IllegalStateException("synthetic claim failure")
                }

            shouldThrow<IllegalStateException> {
                SpringLocalIntegrationMessageBus(listOf(handler), consumption).publish(message)
            }

            observed shouldBe
                mapOf(
                    "message_id" to "message-1",
                    "correlation_id" to "checkout-42",
                    "causation_id" to "order-created-42",
                    "transport_id" to "local",
                )
            MDC.get("message_id") shouldBe null
            MDC.get("correlation_id") shouldBe null
            MDC.get("causation_id") shouldBe null
            MDC.get("transport_id") shouldBe null
        }
    })

@IntegrationMessageType(name = "test.inventory.reserve", version = 1)
private data class TestReserveInventoryCommand(
    val orderId: Long,
    override val occurredAt: Instant,
    private val id: String = "message-1",
) : IntegrationCommand {
    override val messageId: String = id
    override val messageName: String = "test.inventory.reserve"
    override val messageVersion: Int = 1
    override val partitionKey: String = orderId.toString()
    override val correlationId: String = "checkout-42"
    override val causationId: String = "order-created-42"
    override val merchantScopeId: String = "merchant-7"
    override val deploymentScopeId: String = "site-jp"
    override val destination: String = "inventory.commands"

    fun withMessageId(messageId: String) = copy(id = messageId)
}

private val message =
    TestReserveInventoryCommand(
        orderId = 42,
        occurredAt = Instant.parse("2026-08-05T00:00:00Z"),
    )
