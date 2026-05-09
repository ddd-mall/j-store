package com.jstore.accounting.domain.journal.event

import com.jstore.accounting.domain.journal.JournalEntryId
import com.jstore.accounting.domain.journal.JournalEntryType
import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import java.time.Instant
import java.time.LocalDate

@DomainEventType(name = "accounting.journal-entry-posted", version = 1)
data class JournalEntryPostedEvent(
    val entryId: JournalEntryId,
    val entryNo: String,
    val entryType: JournalEntryType,
    val accountingDate: LocalDate,
) : ExplicitDomainEvent {
    override val source: Any get() = entryId
    override val eventName: String get() = "accounting.journal-entry-posted"
    override val eventVersion: Int get() = 1
    override val occurredAt: Instant = Instant.now()
    override val aggregateType: String get() = "JournalEntry"
    override val aggregateId: String get() = entryId.toString()
    override val eventId: String get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}
