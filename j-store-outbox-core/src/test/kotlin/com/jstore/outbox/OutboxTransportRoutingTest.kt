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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class OutboxTransportRoutingTest :
    FunSpec({
        test("router selects the channel matching the persisted transport id") {
            val delivered = mutableListOf<String>()
            val router =
                OutboxDeliveryRouter(
                    listOf(
                        channel("local") { delivered += it.transportId },
                        channel("kafka") { delivered += it.transportId },
                    )
                )

            router.deliver(entry("kafka"))

            delivered shouldBe listOf("kafka")
        }

        test("router rejects a missing transport") {
            val router = OutboxDeliveryRouter(listOf(channel("local") {}))

            shouldThrow<IllegalStateException> { router.deliver(entry("rabbitmq")) }
        }

        test("router rejects duplicate transport implementations") {
            val router = OutboxDeliveryRouter(listOf(channel("kafka") {}, channel("kafka") {}))

            shouldThrow<IllegalStateException> { router.deliver(entry("kafka")) }
        }
    }) {
    companion object {
        private fun channel(
            transportId: String,
            deliver: (OutboxEntry) -> Unit,
        ) =
            object : OutboxDeliveryChannel {
                override val transportId: String = transportId

                override fun deliver(entry: OutboxEntry) = deliver(entry)
            }

        private fun entry(transportId: String) =
            OutboxEntry(
                id = "1",
                eventId = "event-1",
                eventType = "test.event",
                eventVersion = 1,
                aggregateType = "test",
                aggregateId = "1",
                payload = "{}",
                status = OutboxEntryStatus.PENDING,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                messageKind = OutboxMessageKind.INTEGRATION_EVENT,
                deliveryTarget = OutboxDeliveryTarget.BROKER,
                transportId = transportId,
            )
    }
}
