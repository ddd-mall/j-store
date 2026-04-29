package com.jstore.accounting.domain.journal.event

import com.jstore.accounting.domain.journal.JournalEntryId
import com.jstore.common.framework.event.DomainEvent

data class JournalEntryReversedEvent(
    val originalEntryId: JournalEntryId,
    val reversalEntryId: JournalEntryId,
) : DomainEvent {
    override val source: Any get() = originalEntryId
}
