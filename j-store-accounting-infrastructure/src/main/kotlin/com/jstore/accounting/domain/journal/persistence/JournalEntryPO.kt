package com.jstore.accounting.domain.journal.persistence

import com.jstore.accounting.domain.journal.EntrySide
import com.jstore.accounting.domain.journal.JournalEntryStatus
import com.jstore.accounting.domain.journal.JournalEntryType
import com.jstore.accounting.domain.journal.SourceDocumentType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "accounting_journal_entry",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_accounting_journal_source",
            columnNames = ["source_type", "source_id", "source_event_type"],
        )
    ],
)
class JournalEntryPO(
    @Id
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "entry_no", nullable = false, length = 64)
    var entryNo: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 64)
    var entryType: JournalEntryType = JournalEntryType.ORDER_PAYMENT,

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 64)
    var sourceType: SourceDocumentType = SourceDocumentType.ORDER,

    @Column(name = "source_id", nullable = false, length = 128)
    var sourceId: String = "",

    @Column(name = "source_event_type", nullable = false, length = 128)
    var sourceEventType: String = "",

    @Column(name = "accounting_date", nullable = false)
    var accountingDate: LocalDate = LocalDate.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: JournalEntryStatus = JournalEntryStatus.DRAFT,

    @Column(name = "reversed_by")
    var reversedBy: Long? = null,

    @Column(name = "reversal_of")
    var reversalOf: Long? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "posted_at")
    var postedAt: Instant? = null,

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "entry_id")
    var lines: MutableList<JournalLinePO> = mutableListOf(),
)

@Entity
@Table(name = "accounting_journal_line")
class JournalLinePO(
    @Id
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "account_id", nullable = false)
    var accountId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 16)
    var side: EntrySide = EntrySide.DEBIT,

    @Column(name = "amount_fen", nullable = false)
    var amountFen: Long = 0,

    @Column(name = "memo", nullable = false, length = 256)
    var memo: String = "",
)
