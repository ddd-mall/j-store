package com.jstore.common.framework.event

import java.time.Instant

data class StubDomainEvent(
    override val eventId: String = "event-1",
    override val eventName: String = "test.stub-event",
    override val eventVersion: Int = 1,
    override val occurredAt: Instant = Instant.parse("2025-01-01T00:00:00Z"),
    override val aggregateType: String = "Test",
    override val aggregateId: String = "1",
) : DomainEvent
