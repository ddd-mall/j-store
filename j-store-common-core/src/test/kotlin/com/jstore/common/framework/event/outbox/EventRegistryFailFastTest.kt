package com.jstore.common.framework.event.outbox

import com.jstore.common.framework.event.DomainEvent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class EventRegistryFailFastTest : FunSpec({

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
    override val source: Any = "source",
) : DomainEvent

private data class SecondDuplicateEvent(
    override val source: Any = "source",
) : DomainEvent

private class TestUpcaster(
    override val eventName: String,
    override val sourceVersion: Int,
    override val targetVersion: Int,
) : EventUpcaster {
    override fun upcast(payload: String): String = payload
}
