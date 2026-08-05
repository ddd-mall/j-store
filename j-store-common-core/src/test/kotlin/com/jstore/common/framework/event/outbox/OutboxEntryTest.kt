package com.jstore.common.framework.event.outbox

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
