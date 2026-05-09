package com.jstore.accounting.domain.journal.persistence

import com.jstore.accounting.domain.journal.SourceDocumentType
import org.springframework.data.jpa.repository.JpaRepository

interface JournalEntryPOJpaRepository : JpaRepository<JournalEntryPO, Long> {
    fun findBySourceTypeAndSourceIdAndSourceEventType(
        sourceType: SourceDocumentType,
        sourceId: String,
        sourceEventType: String,
    ): JournalEntryPO?
}
