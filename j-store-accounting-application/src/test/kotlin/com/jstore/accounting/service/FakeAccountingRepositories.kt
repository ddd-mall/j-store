package com.jstore.accounting.service

import com.jstore.accounting.domain.account.AccountingSubject
import com.jstore.accounting.domain.account.BalanceDirection
import com.jstore.accounting.domain.account.LedgerAccount
import com.jstore.accounting.domain.account.LedgerAccountCode
import com.jstore.accounting.domain.account.LedgerAccountId
import com.jstore.accounting.domain.account.LedgerAccountImpl
import com.jstore.accounting.domain.account.LedgerAccountRepository
import com.jstore.accounting.domain.account.LedgerAccountStatus
import com.jstore.accounting.domain.account.LedgerAccountType
import com.jstore.accounting.domain.account.SubjectType
import com.jstore.accounting.domain.journal.AccountingBalanceQuery
import com.jstore.accounting.domain.journal.AccountingBalanceView
import com.jstore.accounting.domain.journal.AccountingPeriod
import com.jstore.accounting.domain.journal.AccountingPeriodId
import com.jstore.accounting.domain.journal.AccountingPeriodImpl
import com.jstore.accounting.domain.journal.AccountingPeriodRepository
import com.jstore.accounting.domain.journal.JournalEntry
import com.jstore.accounting.domain.journal.JournalEntryId
import com.jstore.accounting.domain.journal.JournalEntryRepository
import com.jstore.accounting.domain.journal.JournalEntryType
import com.jstore.accounting.domain.journal.JournalLineId
import com.jstore.accounting.domain.journal.PeriodStatus
import com.jstore.accounting.domain.journal.SourceDocument
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

class FakeJournalEntryRepository : JournalEntryRepository {
    private val entries = linkedMapOf<JournalEntryId, JournalEntry>()
    private val sequence = AtomicLong(0)
    var savedCount = 0
    val savedEntries: List<JournalEntry>
        get() = entries.values.toList()

    override fun save(entity: JournalEntry): JournalEntry {
        savedCount++
        entries[entity.id] = entity
        return entity
    }

    override fun findById(id: JournalEntryId): JournalEntry? = entries[id]

    override fun findBySourceDocument(sourceDocument: SourceDocument): JournalEntry? =
        entries.values.firstOrNull { it.sourceDocument == sourceDocument }

    override fun nextId(): JournalEntryId = JournalEntryId(sequence.incrementAndGet())

    override fun nextLineId(): JournalLineId = JournalLineId(sequence.incrementAndGet())

    override fun nextEntryNo(type: JournalEntryType): String =
        "${type.name}-${sequence.incrementAndGet()}"

    override fun summarizeBalance(query: AccountingBalanceQuery): List<AccountingBalanceView> =
        emptyList()
}

class FakeLedgerAccountRepository : LedgerAccountRepository {
    private val accounts =
        listOf(
            account(
                1002,
                "1002",
                SubjectType.PLATFORM,
                "PLATFORM",
                LedgerAccountType.ASSET,
                BalanceDirection.DEBIT,
            ),
            account(
                1010,
                "1010",
                SubjectType.CHANNEL,
                "DEFAULT",
                LedgerAccountType.ASSET,
                BalanceDirection.DEBIT,
            ),
            account(
                2101,
                "2101",
                SubjectType.MERCHANT,
                "DEFAULT",
                LedgerAccountType.LIABILITY,
                BalanceDirection.CREDIT,
            ),
            account(
                3001,
                "3001",
                SubjectType.PLATFORM,
                "PLATFORM",
                LedgerAccountType.REVENUE,
                BalanceDirection.CREDIT,
            ),
        )

    override fun save(entity: LedgerAccount): LedgerAccount = entity

    override fun findById(id: LedgerAccountId): LedgerAccount? = accounts.firstOrNull {
        it.id == id
    }

    override fun findByCodeAndSubject(
        code: LedgerAccountCode,
        subject: AccountingSubject,
    ): LedgerAccount? = accounts.firstOrNull { it.code == code && it.subject == subject }

    private fun account(
        id: Long,
        code: String,
        subjectType: SubjectType,
        subjectId: String,
        type: LedgerAccountType,
        direction: BalanceDirection,
    ) =
        LedgerAccountImpl(
            id = LedgerAccountId(id),
            code = LedgerAccountCode(code),
            name = code,
            type = type,
            direction = direction,
            subject = AccountingSubject(subjectType, subjectId),
            _status = LedgerAccountStatus.ACTIVE,
        )
}

open class FakeAccountingPeriodRepository : AccountingPeriodRepository {
    private val period =
        AccountingPeriodImpl(
            id = AccountingPeriodId(1),
            periodCode = "202604",
            startDate = LocalDate.of(2026, 4, 1),
            endDate = LocalDate.of(2026, 4, 30),
            _status = PeriodStatus.OPEN,
        )

    override fun save(entity: AccountingPeriod): AccountingPeriod = entity

    override fun findById(id: AccountingPeriodId): AccountingPeriod? = period.takeIf { it.id == id }

    override fun findByDate(date: LocalDate): AccountingPeriod? = period.takeIf {
        it.contains(date)
    }
}
