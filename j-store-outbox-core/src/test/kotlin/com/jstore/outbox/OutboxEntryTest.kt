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
            )
        }
    }

    @Test
    fun `retry and fencing counters cannot be negative`() {
        assertFailsWith<IllegalArgumentException> { entry(retryCount = -1) }
        assertFailsWith<IllegalArgumentException> { entry(lockToken = -1) }
    }

    private fun entry(
        status: OutboxEntryStatus = OutboxEntryStatus.PENDING,
        retryCount: Int = 0,
        lockToken: Long = 0,
        deliveryTarget: OutboxDeliveryTarget = OutboxDeliveryTarget.LOCAL_DOMAIN,
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
        )
    }
}
