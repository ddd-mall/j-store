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
    })

@IntegrationMessageType(name = "test.inventory.reserve", version = 1)
private data class TestReserveInventoryCommand(
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

private val message =
    TestReserveInventoryCommand(
        orderId = 42,
        occurredAt = Instant.parse("2026-08-05T00:00:00Z"),
    )
