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

import com.jstore.common.framework.event.LocalDomainEventBus
import com.jstore.common.framework.event.StubDomainEvent
import com.jstore.outbox.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LocalDomainEventDeliveryChannelTest :
    FunSpec({
        test("local domain channel deserializes and synchronously publishes the domain event") {
            val event = StubDomainEvent()
            val serializer = mock<EventSerializer>()
            val bus = mock<LocalDomainEventBus>()
            whenever(serializer.deserialize("{}", "order.created", 3)).thenReturn(event)
            val channel = LocalDomainEventDeliveryChannel(serializer, bus)
            val entry =
                OutboxEntry(
                    id = "entry-1",
                    eventType = "order.created",
                    payload = "{}",
                    aggregateType = "Order",
                    aggregateId = "1",
                    status = OutboxEntryStatus.PENDING,
                    createdAt = Instant.parse("2026-08-05T00:00:00Z"),
                    updatedAt = Instant.parse("2026-08-05T00:00:00Z"),
                    eventVersion = 3,
                )

            channel.deliver(entry)

            channel.transportId shouldBe OutboxTransportIds.LOCAL_DOMAIN
            verify(bus).publishEvent(event)
        }
    })
