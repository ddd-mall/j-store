package com.jstore.accounting.domain.journal

import com.jstore.common.framework.Repository

interface JournalEntryRepository : Repository<JournalEntryId, JournalEntry> {
    fun findBySourceDocument(sourceDocument: SourceDocument): JournalEntry?

    fun nextId(): JournalEntryId

    fun nextLineId(): JournalLineId

    fun nextEntryNo(type: JournalEntryType): String

    fun summarizeBalance(query: AccountingBalanceQuery): List<AccountingBalanceView>
}
