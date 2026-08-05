package com.jstore.common.framework.event.outbox

import com.jstore.common.framework.event.DomainEvent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import java.time.Instant

class EventRegistryFailFastTest :
    FunSpec({
        test("event type registry rejects duplicate event name and version") {
            val registry = InMemoryEventTypeRegistry()
            registry.register("test.duplicate", 1, FirstDuplicateEvent::class.java)

            shouldThrow<IllegalArgumentException> {
                registry.register("test.duplicate", 1, SecondDuplicateEvent::class.java)
            }
        }

        test("event upcaster registry rejects duplicate event name and source version") {
            shouldThrow<IllegalArgumentException> {
                InMemoryEventUpcasterRegistry(
                    listOf(
                        TestUpcaster("test.upcast", sourceVersion = 1, targetVersion = 2),
                        TestUpcaster("test.upcast", sourceVersion = 1, targetVersion = 3),
                    )
                )
            }
        }
    })

private data class FirstDuplicateEvent(
    override val eventId: String = "first",
) : StubDomainEvent

private data class SecondDuplicateEvent(
    override val eventId: String = "second",
) : StubDomainEvent

private interface StubDomainEvent : DomainEvent {
    override val eventName: String
        get() = "test.duplicate"
    override val eventVersion: Int
        get() = 1
    override val occurredAt: Instant
        get() = Instant.EPOCH
    override val aggregateType: String
        get() = "Test"
    override val aggregateId: String
        get() = "1"
}

private class TestUpcaster(
    override val eventName: String,
    override val sourceVersion: Int,
    override val targetVersion: Int,
) : EventUpcaster {
    override fun upcast(payload: String): String = payload
}
