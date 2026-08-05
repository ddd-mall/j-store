/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.accounting.service

import com.jstore.accounting.domain.account.LedgerAccountId
import com.jstore.accounting.domain.journal.EntrySide
import com.jstore.accounting.domain.journal.SourceDocument
import com.jstore.accounting.domain.journal.SourceDocumentType
import com.jstore.accounting.service.command.RecordOrderCompletedCMD
import com.jstore.accounting.service.command.RecordOrderPaidCMD
import com.jstore.accounting.service.command.RecordOrderRefundApprovedCMD
import com.jstore.accounting.service.command.RecordSettlementPaidCMD
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class AccountingApplicationServiceTest :
    FunSpec({
        test("recordOrderPaid is idempotent and does not credit platform commission income") {
            val journalRepo = FakeJournalEntryRepository()
            val service =
                AccountingApplicationService(
                    journalRepo,
                    FakeLedgerAccountRepository(),
                    FakeAccountingPeriodRepository(),
                )
            val cmd =
                RecordOrderPaidCMD(
                    orderId = "1",
                    merchantId = "m1",
                    paidAmount = Price.ofFen(1000),
                    accountingDate = LocalDate.of(2026, 4, 30),
                    sourceDocument =
                        SourceDocument(SourceDocumentType.ORDER, "1", "OrderPaidEvent"),
                )

            val first = (service.recordOrderPaid(cmd) as Success).value
            val second = (service.recordOrderPaid(cmd) as Success).value

            first.id shouldBe second.id
            journalRepo.savedCount shouldBe 1
            first.lines.map { it.accountId } shouldNotContain LedgerAccountId(3001)
            first.lines.first { it.side == EntrySide.DEBIT }.accountId shouldBe
                LedgerAccountId(1010)
            first.lines.first { it.side == EntrySide.CREDIT }.accountId shouldBe
                LedgerAccountId(2101)
        }

        test("recordOrderCompleted confirms platform commission after order completion") {
            val service =
                AccountingApplicationService(
                    FakeJournalEntryRepository(),
                    FakeLedgerAccountRepository(),
                    FakeAccountingPeriodRepository(),
                )
            val entry =
                (service.recordOrderCompleted(
                        RecordOrderCompletedCMD(
                            orderId = "1",
                            merchantId = "m1",
                            commissionAmount = Price.ofFen(100),
                            accountingDate = LocalDate.of(2026, 4, 30),
                            sourceDocument =
                                SourceDocument(
                                    SourceDocumentType.ORDER,
                                    "1",
                                    "OrderCompletedEvent",
                                ),
                        )
                    ) as Success)
                    .value

            entry.lines.first { it.side == EntrySide.DEBIT }.accountId shouldBe
                LedgerAccountId(2101)
            entry.lines.first { it.side == EntrySide.CREDIT }.accountId shouldBe
                LedgerAccountId(3001)
        }

        test("recordOrderRefundApproved creates refund reversal without mutating original source") {
            val journalRepo = FakeJournalEntryRepository()
            val service =
                AccountingApplicationService(
                    journalRepo,
                    FakeLedgerAccountRepository(),
                    FakeAccountingPeriodRepository(),
                )
            val original = SourceDocument(SourceDocumentType.ORDER, "1", "OrderPaidEvent")
            val originalEntry =
                (service.recordOrderPaid(
                        RecordOrderPaidCMD(
                            orderId = "1",
                            merchantId = "m1",
                            paidAmount = Price.ofFen(1000),
                            accountingDate = LocalDate.of(2026, 4, 30),
                            sourceDocument = original,
                        )
                    ) as Success)
                    .value
            val entry =
                (service.recordOrderRefundApproved(
                        RecordOrderRefundApprovedCMD(
                            orderId = "1",
                            merchantId = "m1",
                            refundAmount = Price.ofFen(500),
                            accountingDate = LocalDate.of(2026, 4, 30),
                            sourceDocument =
                                SourceDocument(
                                    SourceDocumentType.REFUND,
                                    "1:item1",
                                    "OrderRefundApprovedEvent",
                                ),
                            originalSourceDocument = original,
                        )
                    ) as Success)
                    .value

            entry.reversalOf shouldBe originalEntry.id
            originalEntry.lines.first { it.side == EntrySide.DEBIT }.amount shouldBe
                Price.ofFen(1000)
            entry.lines.first { it.side == EntrySide.DEBIT }.accountId shouldBe
                LedgerAccountId(2101)
            entry.lines.first { it.side == EntrySide.CREDIT }.accountId shouldBe
                LedgerAccountId(1010)
        }

        test("recordSettlementPaid creates settlement payment journal entry") {
            val journalRepo = FakeJournalEntryRepository()
            val service =
                AccountingApplicationService(
                    journalRepo,
                    FakeLedgerAccountRepository(),
                    FakeAccountingPeriodRepository(),
                )
            val cmd =
                RecordSettlementPaidCMD(
                    settlementId = "10",
                    merchantId = "m1",
                    paidAmount = Price.ofFen(900),
                    accountingDate = LocalDate.of(2026, 4, 30),
                    sourceDocument =
                        SourceDocument(SourceDocumentType.SETTLEMENT, "10", "SettlementPaidEvent"),
                )

            val first = (service.recordSettlementPaid(cmd) as Success).value
            val second = (service.recordSettlementPaid(cmd) as Success).value

            first.id shouldBe second.id
            journalRepo.savedCount shouldBe 1
            first.lines.first { it.side == EntrySide.DEBIT }.accountId shouldBe
                LedgerAccountId(2101)
            first.lines.first { it.side == EntrySide.CREDIT }.accountId shouldBe
                LedgerAccountId(1002)
        }

        test("recordSettlementPaid fails and does not save when accounting period is not open") {
            val journalRepo = FakeJournalEntryRepository()
            val service =
                AccountingApplicationService(
                    journalRepo,
                    FakeLedgerAccountRepository(),
                    MissingAccountingPeriodRepository(),
                )

            val result =
                service.recordSettlementPaid(
                    RecordSettlementPaidCMD(
                        settlementId = "10",
                        merchantId = "m1",
                        paidAmount = Price.ofFen(900),
                        accountingDate = LocalDate.of(2026, 5, 1),
                        sourceDocument =
                            SourceDocument(
                                SourceDocumentType.SETTLEMENT,
                                "10",
                                "SettlementPaidEvent",
                            ),
                    )
                )

            (result is Failure) shouldBe true
            journalRepo.savedCount shouldBe 0
        }

        test("recordOrderRefundApproved fails when original journal entry is missing") {
            val journalRepo = FakeJournalEntryRepository()
            val service =
                AccountingApplicationService(
                    journalRepo,
                    FakeLedgerAccountRepository(),
                    FakeAccountingPeriodRepository(),
                )

            val result =
                service.recordOrderRefundApproved(
                    RecordOrderRefundApprovedCMD(
                        orderId = "1",
                        merchantId = "m1",
                        refundAmount = Price.ofFen(500),
                        accountingDate = LocalDate.of(2026, 4, 30),
                        sourceDocument =
                            SourceDocument(
                                SourceDocumentType.REFUND,
                                "1:item1",
                                "OrderRefundApprovedEvent",
                            ),
                        originalSourceDocument =
                            SourceDocument(SourceDocumentType.ORDER, "1", "OrderPaidEvent"),
                    )
                )

            (result is Failure) shouldBe true
            journalRepo.savedCount shouldBe 0
        }
    })

private class MissingAccountingPeriodRepository : FakeAccountingPeriodRepository() {
    override fun findByDate(date: LocalDate) = null
}
