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
package com.jstore.accounting.domain.journal

import com.jstore.accounting.domain.account.LedgerAccountId
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class JournalEntryUnitTest :
    FunSpec({
        val openPeriod =
            AccountingPeriodImpl(
                id = AccountingPeriodId(1),
                periodCode = "202604",
                startDate = LocalDate.of(2026, 4, 1),
                endDate = LocalDate.of(2026, 4, 30),
                _status = PeriodStatus.OPEN,
            )

        fun draftEntry() =
            JournalEntryImpl(
                id = JournalEntryId(1),
                entryNo = "JE1",
                type = JournalEntryType.ORDER_PAYMENT,
                sourceDocument = SourceDocument(SourceDocumentType.ORDER, "1", "OrderPaidEvent"),
                accountingDate = LocalDate.of(2026, 4, 30),
            )

        test("balanced journal entry can be posted") {
            val entry = draftEntry()
            entry.addLine(
                JournalLine(
                    JournalLineId(1),
                    LedgerAccountId(1010),
                    EntrySide.DEBIT,
                    Price.ofFen(1000),
                    "debit",
                )
            )
            entry.addLine(
                JournalLine(
                    JournalLineId(2),
                    LedgerAccountId(2101),
                    EntrySide.CREDIT,
                    Price.ofFen(1000),
                    "credit",
                )
            )

            entry.post(openPeriod).shouldBe(Success(Unit))
            entry.status shouldBe JournalEntryStatus.POSTED
        }

        test("unbalanced journal entry cannot be posted") {
            val entry = draftEntry()
            entry.addLine(
                JournalLine(
                    JournalLineId(1),
                    LedgerAccountId(1010),
                    EntrySide.DEBIT,
                    Price.ofFen(1000),
                    "debit",
                )
            )
            entry.addLine(
                JournalLine(
                    JournalLineId(2),
                    LedgerAccountId(2101),
                    EntrySide.CREDIT,
                    Price.ofFen(900),
                    "credit",
                )
            )

            entry.post(openPeriod).shouldBe(Failure(AccountingErrors.JOURNAL_ENTRY_UNBALANCED))
        }

        test("posted journal entry cannot be changed and reversal keeps original lines unchanged") {
            val entry = draftEntry()
            entry.addLine(
                JournalLine(
                    JournalLineId(1),
                    LedgerAccountId(1010),
                    EntrySide.DEBIT,
                    Price.ofFen(1000),
                    "debit",
                )
            )
            entry.addLine(
                JournalLine(
                    JournalLineId(2),
                    LedgerAccountId(2101),
                    EntrySide.CREDIT,
                    Price.ofFen(1000),
                    "credit",
                )
            )
            entry.post(openPeriod)
            val originalLines = entry.lines

            entry
                .addLine(
                    JournalLine(
                        JournalLineId(3),
                        LedgerAccountId(3001),
                        EntrySide.CREDIT,
                        Price.ofFen(100),
                        "late",
                    )
                )
                .shouldBe(Failure(AccountingErrors.JOURNAL_ENTRY_ALREADY_POSTED))

            val reversal =
                (entry.createReversal(
                        JournalEntryId(2),
                        "JE2",
                        LocalDate.of(2026, 4, 30),
                        "reverse",
                    ) as Success)
                    .value
            reversal.lines[0].side shouldBe EntrySide.CREDIT
            reversal.lines[1].side shouldBe EntrySide.DEBIT
            entry.lines shouldBe originalLines
        }
    })
