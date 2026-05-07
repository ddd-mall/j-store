package com.jstore.accounting.domain.journal.event

import com.jstore.accounting.domain.journal.JournalEntryId
import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import java.time.Instant

@DomainEventType(name = "accounting.journal-entry-reversed", version = 1)
data class JournalEntryReversedEvent(
    val originalEntryId: JournalEntryId,
    val reversalEntryId: JournalEntryId,
) : ExplicitDomainEvent {
    override val source: Any get() = originalEntryId
    override val eventName: String get() = "accounting.journal-entry-reversed"
    override val eventVersion: Int get() = 1
    override val occurredAt: Instant = Instant.now()
    override val aggregateType: String get() = "JournalEntry"
    override val aggregateId: String get() = originalEntryId.toString()
    override val eventId: String get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}
