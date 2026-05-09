package com.jstore.common.framework.event

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class ExplicitDomainEventTest : FunSpec({

    data class StubExplicitEvent(
        override val source: Any = "aggregate-1",
        override val eventId: String = "event-1",
        override val eventName: String = "catalog.stub-explicit",
        override val eventVersion: Int = 3,
        override val occurredAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        override val aggregateType: String = "Catalog",
        override val aggregateId: String = "aggregate-1",
    ) : ExplicitDomainEvent

    test("explicit domain event supplies stable metadata without reflection fallback") {
        val metadata = StubExplicitEvent().metadata

        metadata.eventId shouldBe "event-1"
        metadata.eventName shouldBe "catalog.stub-explicit"
        metadata.eventVersion shouldBe 3
        metadata.occurredAt shouldBe Instant.parse("2026-01-01T00:00:00Z")
        metadata.aggregateType shouldBe "Catalog"
        metadata.aggregateId shouldBe "aggregate-1"
    }
})
