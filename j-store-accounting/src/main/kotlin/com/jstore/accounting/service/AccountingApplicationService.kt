package com.jstore.accounting.service

import com.jstore.accounting.domain.account.AccountingSubject
import com.jstore.accounting.domain.account.LedgerAccountCode
import com.jstore.accounting.domain.account.LedgerAccountRepository
import com.jstore.accounting.domain.account.SubjectType
import com.jstore.accounting.domain.journal.AccountingPeriodRepository
import com.jstore.accounting.domain.journal.EntrySide
import com.jstore.accounting.domain.journal.JournalEntry
import com.jstore.accounting.domain.journal.JournalEntryImpl
import com.jstore.accounting.domain.journal.JournalEntryRepository
import com.jstore.accounting.domain.journal.JournalEntryType
import com.jstore.accounting.domain.journal.JournalLine
import com.jstore.accounting.domain.journal.SourceDocument
import com.jstore.accounting.service.command.RecordOrderCompletedCMD
import com.jstore.accounting.service.command.RecordOrderPaidCMD
import com.jstore.accounting.service.command.RecordOrderRefundApprovedCMD
import com.jstore.accounting.service.command.RecordSettlementPaidCMD
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure

class AccountingApplicationService(
    private val journalEntryRepository: JournalEntryRepository,
    private val ledgerAccountRepository: LedgerAccountRepository,
    private val accountingPeriodRepository: AccountingPeriodRepository,
) {
    fun findBySourceDocument(sourceDocument: SourceDocument): JournalEntry? =
        journalEntryRepository.findBySourceDocument(sourceDocument)

    fun recordOrderPaid(cmd: RecordOrderPaidCMD): Result<JournalEntry, BusinessError> {
        journalEntryRepository.findBySourceDocument(cmd.sourceDocument)?.let {
            return Success(it)
        }
        val period =
            accountingPeriodRepository.requireOpenPeriod(cmd.accountingDate).let { result ->
                when (result) {
                    is Success -> result.value
                    is Failure -> return Failure(result.error)
                }
            }
        val clearing =
            requireAccount("1010", SubjectType.CHANNEL, "DEFAULT").let { result ->
                when (result) {
                    is Success -> result.value
                    is Failure -> return Failure(result.error)
                }
            }
        val payable =
            requireAccount("2101", SubjectType.MERCHANT, cmd.merchantId).let { result ->
                when (result) {
                    is Success -> result.value
                    is Failure -> return Failure(result.error)
                }
            }
        val entry =
            JournalEntryImpl(
                id = journalEntryRepository.nextId(),
                entryNo = journalEntryRepository.nextEntryNo(JournalEntryType.ORDER_PAYMENT),
                type = JournalEntryType.ORDER_PAYMENT,
                sourceDocument = cmd.sourceDocument,
                accountingDate = cmd.accountingDate,
            )
        entry
            .addLine(
                JournalLine(
                    journalEntryRepository.nextLineId(),
                    clearing.id,
                    EntrySide.DEBIT,
                    cmd.paidAmount,
                    "订单支付代收",
                )
            )
            .onFailure {
                return Failure(it)
            }
        entry
            .addLine(
                JournalLine(
                    journalEntryRepository.nextLineId(),
                    payable.id,
                    EntrySide.CREDIT,
                    cmd.paidAmount,
                    "商户待结算款",
                )
            )
            .onFailure {
                return Failure(it)
            }
        entry.post(period).onFailure {
            return Failure(it)
        }
        return Success(journalEntryRepository.save(entry))
    }

    fun recordOrderCompleted(cmd: RecordOrderCompletedCMD): Result<JournalEntry, BusinessError> {
        journalEntryRepository.findBySourceDocument(cmd.sourceDocument)?.let {
            return Success(it)
        }
        val period =
            accountingPeriodRepository.requireOpenPeriod(cmd.accountingDate).let { result ->
                when (result) {
                    is Success -> result.value
                    is Failure -> return Failure(result.error)
                }
            }
        val payable =
            requireAccount("2101", SubjectType.MERCHANT, cmd.merchantId).let { result ->
                when (result) {
                    is Success -> result.value
                    is Failure -> return Failure(result.error)
                }
            }
        val commission =
            requireAccount("3001", SubjectType.PLATFORM, "PLATFORM").let { result ->
                when (result) {
                    is Success -> result.value
                    is Failure -> return Failure(result.error)
                }
            }
        val entry =
            JournalEntryImpl(
                id = journalEntryRepository.nextId(),
                entryNo =
                    journalEntryRepository.nextEntryNo(
                        JournalEntryType.ORDER_COMPLETION_COMMISSION
                    ),
                type = JournalEntryType.ORDER_COMPLETION_COMMISSION,
                sourceDocument = cmd.sourceDocument,
                accountingDate = cmd.accountingDate,
            )
        entry
            .addLine(
                JournalLine(
                    journalEntryRepository.nextLineId(),
                    payable.id,
                    EntrySide.DEBIT,
                    cmd.commissionAmount,
                    "订单完成确认佣金",
                )
            )
            .onFailure {
                return Failure(it)
            }
        entry
            .addLine(
                JournalLine(
                    journalEntryRepository.nextLineId(),
                    commission.id,
                    EntrySide.CREDIT,
                    cmd.commissionAmount,
                    "平台佣金收入",
                )
            )
            .onFailure {
                return Failure(it)
            }
        entry.post(period).onFailure {
            return Failure(it)
        }
        return Success(journalEntryRepository.save(entry))
    }

    fun recordOrderRefundApproved(
        cmd: RecordOrderRefundApprovedCMD
    ): Result<JournalEntry, BusinessError> {
        journalEntryRepository.findBySourceDocument(cmd.sourceDocument)?.let {
            return Success(it)
        }
        val originalEntry =
            journalEntryRepository.findBySourceDocument(cmd.originalSourceDocument)
                ?: return Failure(
                    com.jstore.accounting.domain.journal.AccountingErrors.JOURNAL_ENTRY_NOT_FOUND
                )
        if (
            originalEntry.status != com.jstore.accounting.domain.journal.JournalEntryStatus.POSTED
        ) {
            return Failure(
                com.jstore.accounting.domain.journal.AccountingErrors.JOURNAL_ENTRY_INVALID_STATE
            )
        }
        val period =
            accountingPeriodRepository.requireOpenPeriod(cmd.accountingDate).let { result ->
                when (result) {
                    is Success -> result.value
                    is Failure -> return Failure(result.error)
                }
            }
        val payable =
            requireAccount("2101", SubjectType.MERCHANT, cmd.merchantId).let { result ->
                when (result) {
                    is Success -> result.value
                    is Failure -> return Failure(result.error)
                }
            }
        val clearing =
            requireAccount("1010", SubjectType.CHANNEL, "DEFAULT").let { result ->
                when (result) {
                    is Success -> result.value
                    is Failure -> return Failure(result.error)
                }
            }
        val entry =
            JournalEntryImpl(
                id = journalEntryRepository.nextId(),
                entryNo =
                    journalEntryRepository.nextEntryNo(JournalEntryType.ORDER_REFUND_REVERSAL),
                type = JournalEntryType.ORDER_REFUND_REVERSAL,
                sourceDocument = cmd.sourceDocument,
                accountingDate = cmd.accountingDate,
                _reversalOf = originalEntry.id,
            )
        entry
            .addLine(
                JournalLine(
                    journalEntryRepository.nextLineId(),
                    payable.id,
                    EntrySide.DEBIT,
                    cmd.refundAmount,
                    "退款冲减商户待结算款",
                )
            )
            .onFailure {
                return Failure(it)
            }
        entry
            .addLine(
                JournalLine(
                    journalEntryRepository.nextLineId(),
                    clearing.id,
                    EntrySide.CREDIT,
                    cmd.refundAmount,
                    "退款冲减支付渠道清算",
                )
            )
            .onFailure {
                return Failure(it)
            }
        entry.post(period).onFailure {
            return Failure(it)
        }
        return Success(journalEntryRepository.save(entry))
    }

    fun recordSettlementPaid(cmd: RecordSettlementPaidCMD): Result<JournalEntry, BusinessError> {
        journalEntryRepository.findBySourceDocument(cmd.sourceDocument)?.let {
            return Success(it)
        }
        val period =
            accountingPeriodRepository.requireOpenPeriod(cmd.accountingDate).let { result ->
                when (result) {
                    is Success -> result.value
                    is Failure -> return Failure(result.error)
                }
            }
        val payable =
            requireAccount("2101", SubjectType.MERCHANT, cmd.merchantId).let { result ->
                when (result) {
                    is Success -> result.value
                    is Failure -> return Failure(result.error)
                }
            }
        val bank =
            requireAccount("1002", SubjectType.PLATFORM, "PLATFORM").let { result ->
                when (result) {
                    is Success -> result.value
                    is Failure -> return Failure(result.error)
                }
            }
        val entry =
            JournalEntryImpl(
                id = journalEntryRepository.nextId(),
                entryNo = journalEntryRepository.nextEntryNo(JournalEntryType.SETTLEMENT_PAYMENT),
                type = JournalEntryType.SETTLEMENT_PAYMENT,
                sourceDocument = cmd.sourceDocument,
                accountingDate = cmd.accountingDate,
            )
        entry
            .addLine(
                JournalLine(
                    journalEntryRepository.nextLineId(),
                    payable.id,
                    EntrySide.DEBIT,
                    cmd.paidAmount,
                    "商户结算打款",
                )
            )
            .onFailure {
                return Failure(it)
            }
        entry
            .addLine(
                JournalLine(
                    journalEntryRepository.nextLineId(),
                    bank.id,
                    EntrySide.CREDIT,
                    cmd.paidAmount,
                    "平台银行存款支付结算款",
                )
            )
            .onFailure {
                return Failure(it)
            }
        entry.post(period).onFailure {
            return Failure(it)
        }
        return Success(journalEntryRepository.save(entry))
    }

    private fun requireAccount(code: String, subjectType: SubjectType, subjectId: String) =
        ledgerAccountRepository
            .findByCodeAndSubject(
                LedgerAccountCode(code),
                AccountingSubject(subjectType, subjectId),
            )
            ?.let { ledgerAccountRepository.requireActive(it.id) }
            ?: if (subjectType == SubjectType.MERCHANT) {
                ledgerAccountRepository
                    .findByCodeAndSubject(
                        LedgerAccountCode(code),
                        AccountingSubject(subjectType, "DEFAULT"),
                    )
                    ?.let { ledgerAccountRepository.requireActive(it.id) }
            } else {
                null
            }
            ?: Failure(
                com.jstore.accounting.domain.account.AccountingAccountErrors.ACCOUNT_NOT_FOUND
            )
}
