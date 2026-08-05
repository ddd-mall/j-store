package com.jstore.accounting.domain.journal.event

import com.jstore.accounting.domain.journal.JournalEntryId
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import java.time.Instant

@DomainEventType(name = "accounting.journal-entry-reversed", version = 1)
data class JournalEntryReversedEvent(
    val originalEntryId: JournalEntryId,
    val reversalEntryId: JournalEntryId,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {

    override val eventName: String
        get() = "accounting.journal-entry-reversed"

    override val eventVersion: Int
        get() = 1

    override val aggregateType: String
        get() = "JournalEntry"

    override val aggregateId: String
        get() = originalEntryId.toString()

}
