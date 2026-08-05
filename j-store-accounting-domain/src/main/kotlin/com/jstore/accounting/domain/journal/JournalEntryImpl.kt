package com.jstore.accounting.domain.journal

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.EventRecordingAggregateRoot
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.Instant
import java.time.LocalDate

class JournalEntryImpl(
    override val id: JournalEntryId,
    override val entryNo: String,
    override val type: JournalEntryType,
    override val sourceDocument: SourceDocument,
    override val accountingDate: LocalDate,
    private val _lines: MutableList<JournalLine> = mutableListOf(),
    private var _status: JournalEntryStatus = JournalEntryStatus.DRAFT,
    override val createdAt: Instant = Instant.now(),
    private var _postedAt: Instant? = null,
    private var _reversedBy: JournalEntryId? = null,
    private var _reversalOf: JournalEntryId? = null,
) : EventRecordingAggregateRoot<JournalEntryId>(), JournalEntry {

    init {
        require(entryNo.isNotBlank()) { "账务凭证号不能为空" }
    }

    override val status: JournalEntryStatus
        get() = _status

    override val lines: List<JournalLine>
        get() = _lines.toList()

    override val postedAt: Instant?
        get() = _postedAt

    override val reversedBy: JournalEntryId?
        get() = _reversedBy

    override val reversalOf: JournalEntryId?
        get() = _reversalOf

    override fun addLine(line: JournalLine): Result<Unit, BusinessError> {
        if (_status != JournalEntryStatus.DRAFT) {
            return Failure(AccountingErrors.JOURNAL_ENTRY_ALREADY_POSTED)
        }
        _lines.add(line)
        return Success(Unit)
    }

    override fun post(openPeriod: AccountingPeriod): Result<Unit, BusinessError> {
        if (_status != JournalEntryStatus.DRAFT) {
            return Failure(AccountingErrors.JOURNAL_ENTRY_ALREADY_POSTED)
        }
        if (openPeriod.status != PeriodStatus.OPEN || !openPeriod.contains(accountingDate)) {
            return Failure(AccountingErrors.ACCOUNTING_PERIOD_CLOSED)
        }
        if (_lines.size < 2) {
            return Failure(AccountingErrors.JOURNAL_ENTRY_LINES_INSUFFICIENT)
        }
        if (!isBalanced()) {
            return Failure(AccountingErrors.JOURNAL_ENTRY_UNBALANCED)
        }
        _status = JournalEntryStatus.POSTED
        _postedAt = Instant.now()
        return Success(Unit)
    }

    override fun markReversed(reversalEntryId: JournalEntryId): Result<Unit, BusinessError> {
        if (_status != JournalEntryStatus.POSTED) {
            return Failure(AccountingErrors.JOURNAL_ENTRY_INVALID_STATE)
        }
        _status = JournalEntryStatus.REVERSED
        _reversedBy = reversalEntryId
        return Success(Unit)
    }

    override fun createReversal(
        reversalEntryId: JournalEntryId,
        reversalEntryNo: String,
        accountingDate: LocalDate,
        reason: String,
    ): Result<JournalEntry, BusinessError> {
        if (_status != JournalEntryStatus.POSTED) {
            return Failure(AccountingErrors.JOURNAL_ENTRY_INVALID_STATE)
        }
        if (reason.isBlank()) {
            return Failure(AccountingErrors.JOURNAL_ENTRY_INVALID_STATE.msg("冲正原因不能为空"))
        }
        val reversal =
            JournalEntryImpl(
                id = reversalEntryId,
                entryNo = reversalEntryNo,
                type = JournalEntryType.MANUAL_ADJUSTMENT,
                sourceDocument =
                    SourceDocument(
                        sourceType = SourceDocumentType.ADJUSTMENT,
                        sourceId = id.value.toString(),
                        eventType = "JournalEntryReversed",
                    ),
                accountingDate = accountingDate,
                _reversalOf = id,
            )
        _lines.forEach { line ->
            reversal.addLine(
                line.copy(
                    side =
                        when (line.side) {
                            EntrySide.DEBIT -> EntrySide.CREDIT
                            EntrySide.CREDIT -> EntrySide.DEBIT
                        },
                    memo = reason,
                )
            )
        }
        return Success(reversal)
    }

    private fun isBalanced(): Boolean {
        fun sum(side: EntrySide): Price =
            Price.sumOf(_lines.filter { it.side == side }.map { it.amount })
        return sum(EntrySide.DEBIT) == sum(EntrySide.CREDIT)
    }
}
