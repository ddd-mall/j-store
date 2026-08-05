package com.jstore.accounting.domain.journal.event

import com.jstore.accounting.domain.journal.JournalEntryId
import com.jstore.accounting.domain.journal.JournalEntryType
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import java.time.Instant
import java.time.LocalDate

@DomainEventType(name = "accounting.journal-entry-posted", version = 1)
data class JournalEntryPostedEvent(
    val entryId: JournalEntryId,
    val entryNo: String,
    val entryType: JournalEntryType,
    val accountingDate: LocalDate,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {

    override val eventName: String
        get() = "accounting.journal-entry-posted"

    override val eventVersion: Int
        get() = 1

    override val aggregateType: String
        get() = "JournalEntry"

    override val aggregateId: String
        get() = entryId.toString()

}
