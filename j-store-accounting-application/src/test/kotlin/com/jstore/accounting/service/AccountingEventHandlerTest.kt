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
import com.jstore.contracts.commerce.ContractRefundItem
import com.jstore.contracts.commerce.OrderCompletedIntegrationEvent
import com.jstore.contracts.commerce.PaymentCapturedIntegrationEvent
import com.jstore.contracts.commerce.PaymentRefundSucceededIntegrationEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class AccountingEventHandlerTest :
    FunSpec({
        test("PaymentCapturedEvent is converted into order payment journal entry") {
            val journalRepo = FakeJournalEntryRepository()
            val app =
                AccountingApplicationService(
                    journalRepo,
                    FakeLedgerAccountRepository(),
                    FakeAccountingPeriodRepository(),
                )
            val handler = PaymentCapturedAccountingEventHandler(FakeOrderAccountingService(), app)

            handler.handle(
                PaymentCapturedIntegrationEvent(
                    paymentId = 2,
                    orderId = 1,
                    merchantId = 10,
                    providerTransactionId = "txn-1",
                    amountFen = 1000,
                    currency = "CNY",
                    sourceMessageId = "payment-captured-1",
                    occurredAtValue = Instant.parse("2026-04-30T01:00:00Z"),
                )
            )

            journalRepo.savedCount shouldBe 1
            journalRepo.savedEntries.single().sourceDocument shouldBe
                SourceDocument(SourceDocumentType.ORDER, "1", "PaymentCapturedEvent")
        }

        test("OrderCompletedEvent is converted into commission journal entry") {
            val journalRepo = FakeJournalEntryRepository()
            val app =
                AccountingApplicationService(
                    journalRepo,
                    FakeLedgerAccountRepository(),
                    FakeAccountingPeriodRepository(),
                )
            val handler = OrderCompletedAccountingEventHandler(FakeOrderAccountingService(), app)

            handler.handle(
                OrderCompletedIntegrationEvent(
                    orderId = 1,
                    sourceMessageId = "order-completed-1",
                    occurredAtValue = Instant.parse("2026-04-30T01:00:00Z"),
                )
            )

            journalRepo.savedEntries.single().sourceDocument shouldBe
                SourceDocument(SourceDocumentType.ORDER, "1", "OrderCompletedEvent")
        }

        test("OrderRefundApprovedEvent is converted into refund reversal journal entry") {
            val journalRepo = FakeJournalEntryRepository()
            val app =
                AccountingApplicationService(
                    journalRepo,
                    FakeLedgerAccountRepository(),
                    FakeAccountingPeriodRepository(),
                )
            app.recordOrderPaid(
                RecordOrderPaidCMD(
                    orderId = "1",
                    merchantId = "m1",
                    paidAmount = Price.ofFen(1000),
                    accountingDate = java.time.LocalDate.of(2026, 4, 30),
                    sourceDocument =
                        SourceDocument(SourceDocumentType.ORDER, "1", "PaymentCapturedEvent"),
                )
            )
            val handler =
                PaymentRefundSucceededAccountingEventHandler(FakeOrderAccountingService(), app)

            handler.handle(
                PaymentRefundSucceededIntegrationEvent(
                    paymentId = 2,
                    refundId = 9,
                    orderId = 1,
                    afterSaleId = 8,
                    merchantId = 10,
                    providerRefundId = "refund-1",
                    items = listOf(ContractRefundItem(10, 20, 1, 500)),
                    amountFen = 500,
                    currency = "CNY",
                    sourceMessageId = "refund-succeeded-1",
                    occurredAtValue = Instant.parse("2026-04-30T01:00:00Z"),
                )
            )

            journalRepo.savedEntries.last().sourceDocument shouldBe
                SourceDocument(SourceDocumentType.REFUND, "9", "PaymentRefundSucceededEvent")
        }

        test("SettlementPaidEvent is converted into settlement payment journal entry") {
            val journalRepo = FakeJournalEntryRepository()
            val app =
                AccountingApplicationService(
                    journalRepo,
                    FakeLedgerAccountRepository(),
                    FakeAccountingPeriodRepository(),
                )
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

            journalRepo.savedEntries.single().sourceDocument shouldBe
                SourceDocument(SourceDocumentType.SETTLEMENT, "10", "SettlementPaidEvent")
        }
    })

private class FakeOrderAccountingService : AccountingOrderService {
    override fun getOrderAccountingInfo(
        orderId: String
    ): Result<OrderAccountingInfo, BusinessError> =
        Success(
            OrderAccountingInfo(
                orderId = orderId,
                merchantId = "m1",
                paidAmount = Price.ofFen(1000),
                commissionAmount = Price.ofFen(100),
                completedAt = Instant.parse("2026-04-30T01:00:00Z"),
            )
        )

    override fun getRefundableOriginalSource(
        orderId: String
    ): Result<SourceDocument, BusinessError> =
        Success(SourceDocument(SourceDocumentType.ORDER, orderId, "PaymentCapturedEvent"))
}
