package com.jstore.common.framework.event.outbox

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventBus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.*
import java.time.Instant

/**
 * OutboxPublisher 单元测试
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 3.1, 3.5, 7.1, 7.2, 7.3
 */
class OutboxPublisherTest : FunSpec({

    data class StubEvent(override val source: Any = "stub") : DomainEvent

    fun createEntry(
        id: String = "entry-1",
        retryCount: Int = 0,
        status: OutboxEntryStatus = OutboxEntryStatus.PENDING
    ) = OutboxEntry(
        id = id,
        eventType = "com.example.StubEvent",
        payload = """{"source":"stub"}""",
        aggregateType = "Order",
        aggregateId = "42",
        status = status,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
        retryCount = retryCount
    )

    test("poll, deliver, and update status to PUBLISHED on success") {
        val entry = createEntry()
        val mockRepo = mock<OutboxEntryRepository> {
            on { findPendingAndRetryable(any(), any()) } doReturn listOf(entry)
            on { save(any()) } doAnswer { it.arguments[0] as OutboxEntry }
        }
        val mockSerializer = mock<EventSerializer> {
            on { deserialize(any(), any()) } doReturn StubEvent()
        }
        val mockBus = mock<DomainEventBus>()
        val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

        val publisher = OutboxPublisher(mockRepo, mockSerializer, mockBus, properties)
        publisher.pollAndPublish()

        verify(mockBus).publishEvent(any())
        val captor = argumentCaptor<OutboxEntry>()
        verify(mockRepo).save(captor.capture())
        captor.firstValue.status shouldBe OutboxEntryStatus.PUBLISHED
    }

    test("delivery failure increments retryCount and sets FAILED status") {
        val entry = createEntry(retryCount = 1)
        val mockRepo = mock<OutboxEntryRepository> {
            on { findPendingAndRetryable(any(), any()) } doReturn listOf(entry)
            on { save(any()) } doAnswer { it.arguments[0] as OutboxEntry }
        }
        val mockSerializer = mock<EventSerializer> {
            on { deserialize(any(), any()) } doReturn StubEvent()
        }
        val mockBus = mock<DomainEventBus> {
            on { publishEvent(any()) } doThrow RuntimeException("bus error")
        }
        val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

        val publisher = OutboxPublisher(mockRepo, mockSerializer, mockBus, properties)
        publisher.pollAndPublish()

        val captor = argumentCaptor<OutboxEntry>()
        verify(mockRepo).save(captor.capture())
        captor.firstValue.retryCount shouldBe 2
        captor.firstValue.status shouldBe OutboxEntryStatus.FAILED
    }

    test("delivery failure at max retry sets DEAD_LETTER status") {
        val entry = createEntry(retryCount = 4)
        val mockRepo = mock<OutboxEntryRepository> {
            on { findPendingAndRetryable(any(), any()) } doReturn listOf(entry)
            on { save(any()) } doAnswer { it.arguments[0] as OutboxEntry }
        }
        val mockSerializer = mock<EventSerializer> {
            on { deserialize(any(), any()) } doReturn StubEvent()
        }
        val mockBus = mock<DomainEventBus> {
            on { publishEvent(any()) } doThrow RuntimeException("bus error")
        }
        val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

        val publisher = OutboxPublisher(mockRepo, mockSerializer, mockBus, properties)
        publisher.pollAndPublish()

        val captor = argumentCaptor<OutboxEntry>()
        verify(mockRepo).save(captor.capture())
        captor.firstValue.retryCount shouldBe 5
        captor.firstValue.status shouldBe OutboxEntryStatus.DEAD_LETTER
    }

    test("top-level exception does not interrupt scheduling") {
        val mockRepo = mock<OutboxEntryRepository> {
            on { findPendingAndRetryable(any(), any()) } doThrow RuntimeException("DB connection lost")
        }
        val mockSerializer = mock<EventSerializer>()
        val mockBus = mock<DomainEventBus>()
        val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

        val publisher = OutboxPublisher(mockRepo, mockSerializer, mockBus, properties)

        // Should NOT throw — top-level catch prevents scheduling interruption
        publisher.pollAndPublish()

        // Verify no delivery was attempted
        verify(mockBus, never()).publishEvent(any())
    }

    test("empty poll results in no delivery attempts") {
        val mockRepo = mock<OutboxEntryRepository> {
            on { findPendingAndRetryable(any(), any()) } doReturn emptyList()
            on { save(any()) } doAnswer { it.arguments[0] as OutboxEntry }
        }
        val mockSerializer = mock<EventSerializer>()
        val mockBus = mock<DomainEventBus>()
        val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

        val publisher = OutboxPublisher(mockRepo, mockSerializer, mockBus, properties)
        publisher.pollAndPublish()

        verify(mockBus, never()).publishEvent(any())
        verify(mockRepo, never()).save(any())
    }

    test("multiple entries: one failure does not prevent others from being delivered") {
        val entry1 = createEntry(id = "entry-1")
        val entry2 = createEntry(id = "entry-2")
        val entry3 = createEntry(id = "entry-3")

        val stubEvent = StubEvent()
        var callCount = 0
        val mockRepo = mock<OutboxEntryRepository> {
            on { findPendingAndRetryable(any(), any()) } doReturn listOf(entry1, entry2, entry3)
            on { save(any()) } doAnswer { it.arguments[0] as OutboxEntry }
        }
        val mockSerializer = mock<EventSerializer> {
            on { deserialize(any(), any()) } doReturn stubEvent
        }
        val mockBus = mock<DomainEventBus> {
            on { publishEvent(any()) } doAnswer {
                callCount++
                if (callCount == 2) throw RuntimeException("second delivery fails")
            }
        }
        val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

        val publisher = OutboxPublisher(mockRepo, mockSerializer, mockBus, properties)
        publisher.pollAndPublish()

        // All 3 entries should have been saved (2 PUBLISHED, 1 FAILED)
        val captor = argumentCaptor<OutboxEntry>()
        verify(mockRepo, times(3)).save(captor.capture())

        captor.allValues[0].status shouldBe OutboxEntryStatus.PUBLISHED
        captor.allValues[1].status shouldBe OutboxEntryStatus.FAILED
        captor.allValues[2].status shouldBe OutboxEntryStatus.PUBLISHED
    }
})
