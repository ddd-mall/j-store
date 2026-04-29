package com.jstore.accounting.service

import com.jstore.accounting.domain.account.LedgerAccountId
import com.jstore.accounting.domain.journal.EntrySide
import com.jstore.accounting.domain.journal.SourceDocument
import com.jstore.accounting.domain.journal.SourceDocumentType
import com.jstore.accounting.service.command.RecordOrderPaidCMD
import com.jstore.accounting.service.command.RecordOrderCompletedCMD
import com.jstore.accounting.service.command.RecordOrderRefundApprovedCMD
import com.jstore.common.properties.Price
import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class AccountingApplicationServiceTest : FunSpec({
    test("recordOrderPaid is idempotent and does not credit platform commission income") {
        val journalRepo = FakeJournalEntryRepository()
        val service = AccountingApplicationService(journalRepo, FakeLedgerAccountRepository(), FakeAccountingPeriodRepository())
        val cmd = RecordOrderPaidCMD(
            orderId = "1",
            merchantId = "m1",
            paidAmount = Price.ofFen(1000),
            accountingDate = LocalDate.of(2026, 4, 30),
            sourceDocument = SourceDocument(SourceDocumentType.ORDER, "1", "OrderPaidEvent"),
        )

        val first = (service.recordOrderPaid(cmd) as Success).value
        val second = (service.recordOrderPaid(cmd) as Success).value

        first.id shouldBe second.id
        journalRepo.savedCount shouldBe 1
        first.lines.map { it.accountId } shouldNotContain LedgerAccountId(3001)
        first.lines.first { it.side == EntrySide.DEBIT }.accountId shouldBe LedgerAccountId(1010)
        first.lines.first { it.side == EntrySide.CREDIT }.accountId shouldBe LedgerAccountId(2101)
    }

    test("recordOrderCompleted confirms platform commission after order completion") {
        val service = AccountingApplicationService(FakeJournalEntryRepository(), FakeLedgerAccountRepository(), FakeAccountingPeriodRepository())
        val entry = (service.recordOrderCompleted(
            RecordOrderCompletedCMD(
                orderId = "1",
                merchantId = "m1",
                commissionAmount = Price.ofFen(100),
                accountingDate = LocalDate.of(2026, 4, 30),
                sourceDocument = SourceDocument(SourceDocumentType.ORDER, "1", "OrderCompletedEvent"),
            )
        ) as Success).value

        entry.lines.first { it.side == EntrySide.DEBIT }.accountId shouldBe LedgerAccountId(2101)
        entry.lines.first { it.side == EntrySide.CREDIT }.accountId shouldBe LedgerAccountId(3001)
    }

    test("recordOrderRefundApproved creates refund reversal without mutating original source") {
        val service = AccountingApplicationService(FakeJournalEntryRepository(), FakeLedgerAccountRepository(), FakeAccountingPeriodRepository())
        val original = SourceDocument(SourceDocumentType.ORDER, "1", "OrderPaidEvent")
        val entry = (service.recordOrderRefundApproved(
            RecordOrderRefundApprovedCMD(
                orderId = "1",
                merchantId = "m1",
                refundAmount = Price.ofFen(500),
                accountingDate = LocalDate.of(2026, 4, 30),
                sourceDocument = SourceDocument(SourceDocumentType.REFUND, "1:item1", "OrderRefundApprovedEvent"),
                originalSourceDocument = original,
            )
        ) as Success).value

        entry.lines.first { it.side == EntrySide.DEBIT }.accountId shouldBe LedgerAccountId(2101)
        entry.lines.first { it.side == EntrySide.CREDIT }.accountId shouldBe LedgerAccountId(1010)
    }
})
