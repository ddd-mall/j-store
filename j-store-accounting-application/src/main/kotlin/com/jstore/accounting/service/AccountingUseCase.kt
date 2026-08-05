package com.jstore.accounting.service

import com.jstore.accounting.domain.journal.JournalEntry
import com.jstore.accounting.domain.journal.SourceDocument
import com.jstore.accounting.service.command.RecordOrderCompletedCMD
import com.jstore.accounting.service.command.RecordOrderPaidCMD
import com.jstore.accounting.service.command.RecordOrderRefundApprovedCMD
import com.jstore.accounting.service.command.RecordSettlementPaidCMD
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result

interface AccountingUseCase {
    fun findBySourceDocument(sourceDocument: SourceDocument): JournalEntry?
    fun recordOrderPaid(cmd: RecordOrderPaidCMD): Result<JournalEntry, BusinessError>
    fun recordOrderCompleted(cmd: RecordOrderCompletedCMD): Result<JournalEntry, BusinessError>
    fun recordOrderRefundApproved(cmd: RecordOrderRefundApprovedCMD): Result<JournalEntry, BusinessError>
    fun recordSettlementPaid(cmd: RecordSettlementPaidCMD): Result<JournalEntry, BusinessError>
}
