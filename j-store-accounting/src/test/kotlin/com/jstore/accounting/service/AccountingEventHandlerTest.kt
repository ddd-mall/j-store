package com.jstore.accounting.service

import com.jstore.accounting.acl.AccountingOrderService
import com.jstore.accounting.acl.OrderAccountingInfo
import com.jstore.accounting.domain.journal.SourceDocument
import com.jstore.accounting.domain.journal.SourceDocumentType
import com.jstore.accounting.domain.settlement.SettlementStatementId
import com.jstore.accounting.domain.settlement.event.SettlementPaidEvent
import com.jstore.accounting.service.command.RecordOrderPaidCMD
import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Price
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import com.jstore.order.domain.order.event.OrderCompletedEvent
import com.jstore.order.domain.order.event.OrderPaidEvent
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.aftersale.event.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class AccountingEventHandlerTest : FunSpec({
    test("OrderPaidEvent is converted into order payment journal entry") {
        val journalRepo = FakeJournalEntryRepository()
        val app = AccountingApplicationService(journalRepo, FakeLedgerAccountRepository(), FakeAccountingPeriodRepository())
        val handler = OrderPaidAccountingEventHandler(FakeOrderAccountingService(), app)

        handler.onDomainEvent(OrderPaidEvent(OrderId(1), Price.ofFen(1000), emptyList(), Instant.parse("2026-04-30T01:00:00Z")))

        journalRepo.savedCount shouldBe 1
        journalRepo.savedEntries.single().sourceDocument shouldBe SourceDocument(SourceDocumentType.ORDER, "1", "OrderPaidEvent")
    }

    test("OrderCompletedEvent is converted into commission journal entry") {
        val journalRepo = FakeJournalEntryRepository()
        val app = AccountingApplicationService(journalRepo, FakeLedgerAccountRepository(), FakeAccountingPeriodRepository())
        val handler = OrderCompletedAccountingEventHandler(FakeOrderAccountingService(), app)

        handler.onDomainEvent(OrderCompletedEvent(OrderId(1), Instant.parse("2026-04-30T01:00:00Z")))

        journalRepo.savedEntries.single().sourceDocument shouldBe SourceDocument(SourceDocumentType.ORDER, "1", "OrderCompletedEvent")
    }

    test("OrderRefundApprovedEvent is converted into refund reversal journal entry") {
        val journalRepo = FakeJournalEntryRepository()
        val app = AccountingApplicationService(journalRepo, FakeLedgerAccountRepository(), FakeAccountingPeriodRepository())
        app.recordOrderPaid(
            RecordOrderPaidCMD(
                orderId = "1",
                merchantId = "m1",
                paidAmount = Price.ofFen(1000),
                accountingDate = java.time.LocalDate.of(2026, 4, 30),
                sourceDocument = SourceDocument(SourceDocumentType.ORDER, "1", "OrderPaidEvent"),
            )
        )
        val handler = OrderRefundApprovedAccountingEventHandler(FakeOrderAccountingService(), app)

        handler.onDomainEvent(
            AfterSaleApprovedEvent(
                afterSaleId = AfterSaleId(9),
                orderId = OrderId(1),
                merchantId = MerchantActorId(1),
                items = listOf(AfterSaleEventItem(OrderItemId(10), 20, 1, Price.ofFen(500), "CNY")),
                requireReturn = false,
                occurredAt = Instant.parse("2026-04-30T01:00:00Z"),
            )
        )

        journalRepo.savedEntries.last().sourceDocument shouldBe SourceDocument(SourceDocumentType.REFUND, "9", "AfterSaleApprovedEvent")
    }

    test("SettlementPaidEvent is converted into settlement payment journal entry") {
        val journalRepo = FakeJournalEntryRepository()
        val app = AccountingApplicationService(journalRepo, FakeLedgerAccountRepository(), FakeAccountingPeriodRepository())
        val handler = SettlementPaidAccountingEventHandler(app)

        handler.onDomainEvent(
            SettlementPaidEvent(
                settlementId = SettlementStatementId(10),
                statementNo = "ST10",
                merchantId = "m1",
                payableAmount = Price.ofFen(900),
                paidAt = Instant.parse("2026-04-30T01:00:00Z"),
            )
        )

        journalRepo.savedEntries.single().sourceDocument shouldBe SourceDocument(SourceDocumentType.SETTLEMENT, "10", "SettlementPaidEvent")
    }
})

private class FakeOrderAccountingService : AccountingOrderService {
    override fun getOrderAccountingInfo(orderId: String): Result<OrderAccountingInfo, BusinessError> =
        Success(
            OrderAccountingInfo(
                orderId = orderId,
                merchantId = "m1",
                paidAmount = Price.ofFen(1000),
                commissionAmount = Price.ofFen(100),
                completedAt = Instant.parse("2026-04-30T01:00:00Z"),
            )
        )

    override fun getRefundableOriginalSource(orderId: String): Result<SourceDocument, BusinessError> =
        Success(SourceDocument(SourceDocumentType.ORDER, orderId, "OrderPaidEvent"))
}
