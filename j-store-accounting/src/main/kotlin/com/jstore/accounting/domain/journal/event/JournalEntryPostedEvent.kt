package com.jstore.accounting.domain.journal.event

import com.jstore.accounting.domain.journal.JournalEntryId
import com.jstore.accounting.domain.journal.JournalEntryType
import com.jstore.common.framework.event.DomainEvent
import java.time.LocalDate

data class JournalEntryPostedEvent(
    val entryId: JournalEntryId,
    val entryNo: String,
    val entryType: JournalEntryType,
    val accountingDate: LocalDate,
) : DomainEvent {
    override val source: Any get() = entryId
}
