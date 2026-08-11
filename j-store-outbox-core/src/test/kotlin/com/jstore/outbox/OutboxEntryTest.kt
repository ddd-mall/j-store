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

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

class OutboxEntryTest {
    @Test
    fun `domain event cannot target an integration channel`() {
        assertFailsWith<IllegalArgumentException> {
            entry(deliveryTarget = OutboxDeliveryTarget.BROKER)
        }
    }

    @Test
    fun `in progress entry requires a complete valid lease`() {
        assertFailsWith<IllegalArgumentException> {
            entry(status = OutboxEntryStatus.IN_PROGRESS)
        }
    }

    @Test
    fun `integration target and transport id must agree`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        assertFailsWith<IllegalArgumentException> {
            OutboxEntry(
                id = "integration-1",
                eventType = "test.command",
                payload = "{}",
                aggregateType = "Test",
                aggregateId = "1",
                status = OutboxEntryStatus.PENDING,
                createdAt = now,
                updatedAt = now,
                messageKind = OutboxMessageKind.INTEGRATION_COMMAND,
                deliveryTarget = OutboxDeliveryTarget.BROKER,
                transportId = OutboxTransportIds.LOCAL,
                orderingKey = "4:Test:1:1",
                sequenceNo = 1,
            )
        }
    }

    @Test
    fun `retry and fencing counters cannot be negative`() {
        assertFailsWith<IllegalArgumentException> { entry(retryCount = -1) }
        assertFailsWith<IllegalArgumentException> { entry(lockToken = -1) }
    }

    @Test
    fun `ordering stream metadata must be stable and positive`() {
        assertFailsWith<IllegalArgumentException> { entry(orderingKey = " ") }
        assertFailsWith<IllegalArgumentException> { entry(sequenceNo = 0) }
    }

    @Test
    fun `ordering keys include their business scope`() {
        kotlin.test.assertEquals(
            "963b3779794e5b98ee843f43c56811bebc9ed53050f0861c47612b0b6b3dd089",
            OutboxOrderingKeys.domain("Order", "42"),
        )
        kotlin.test.assertEquals(
            "57d0d731fe2cefe49a264ba7e12c17ae2b8de8fae70f4703504532a64e200f47",
            OutboxOrderingKeys.integration("inventory.commands", "42"),
        )
        kotlin.test.assertEquals(
            "f1a476be28e1db6b844cd08b189fdb4dab3db81545105da5711f10d336aa9193",
            OutboxOrderingKeys.integration("订单🚀", "客户🚀"),
        )
        kotlin.test.assertEquals(
            64,
            OutboxOrderingKeys.integration("d".repeat(512), "k".repeat(256)).length,
        )
        kotlin.test.assertNotEquals(
            OutboxOrderingKeys.domain("a:b", "c"),
            OutboxOrderingKeys.domain("a", "b:c"),
        )
    }

    private fun entry(
        status: OutboxEntryStatus = OutboxEntryStatus.PENDING,
        retryCount: Int = 0,
        lockToken: Long = 0,
        deliveryTarget: OutboxDeliveryTarget = OutboxDeliveryTarget.LOCAL_DOMAIN,
        orderingKey: String = "Test:1",
        sequenceNo: Long = 1,
    ): OutboxEntry {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        return OutboxEntry(
            id = "1",
            eventType = "test.event",
            payload = "{}",
            aggregateType = "Test",
            aggregateId = "1",
            status = status,
            createdAt = now,
            updatedAt = now,
            retryCount = retryCount,
            lockToken = lockToken,
            deliveryTarget = deliveryTarget,
            orderingKey = orderingKey,
            sequenceNo = sequenceNo,
        )
    }
}
