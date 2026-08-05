package com.jstore.accounting.domain.journal

import com.jstore.common.framework.AggregateRepository

interface JournalEntryRepository : AggregateRepository<JournalEntryId, JournalEntry> {
    fun findBySourceDocument(sourceDocument: SourceDocument): JournalEntry?

    fun nextId(): JournalEntryId

    fun nextLineId(): JournalLineId

    fun nextEntryNo(type: JournalEntryType): String

    fun summarizeBalance(query: AccountingBalanceQuery): List<AccountingBalanceView>
}
