package com.jstore.common.framework.event

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class DomainEventContractTest :
    FunSpec({
        test("domain event supplies stable metadata without reflection fallback") {
            val metadata =
                StubDomainEvent(
                    eventId = "event-1",
                    eventName = "catalog.stub-explicit",
                    eventVersion = 3,
                    occurredAt = Instant.parse("2026-01-01T00:00:00Z"),
                    aggregateType = "Catalog",
                    aggregateId = "aggregate-1",
                ).metadata

            metadata.eventId shouldBe "event-1"
            metadata.eventName shouldBe "catalog.stub-explicit"
            metadata.eventVersion shouldBe 3
            metadata.occurredAt shouldBe Instant.parse("2026-01-01T00:00:00Z")
            metadata.aggregateType shouldBe "Catalog"
            metadata.aggregateId shouldBe "aggregate-1"
        }
    })
