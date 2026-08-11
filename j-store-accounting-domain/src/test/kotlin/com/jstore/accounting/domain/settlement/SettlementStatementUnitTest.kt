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
package com.jstore.accounting.domain.settlement

import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import java.time.LocalDate

class SettlementStatementUnitTest :
    FunSpec({
        fun statement() =
            SettlementStatementImpl(
                id = SettlementStatementId(1),
                statementNo = "ST1",
                merchantId = "m1",
                period = SettlementPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)),
            )

        test("settlement payable amount equals sum of net amounts") {
            val statement = statement()
            statement.addLine(
                SettlementLine(
                    SettlementLineId(1),
                    "o1",
                    Price.ofFen(1000),
                    Price.ZERO,
                    Price.ofFen(100),
                    Price.ofFen(900),
                )
            )
            statement.addLine(
                SettlementLine(
                    SettlementLineId(2),
                    "o2",
                    Price.ofFen(2000),
                    Price.ZERO,
                    Price.ofFen(200),
                    Price.ofFen(1800),
                )
            )

            statement.payableAmount shouldBe Price.ofFen(2700)
            statement.confirm().shouldBe(Success(Unit))
            statement.status shouldBe SettlementStatementStatus.CONFIRMED
        }

        test("paid transition only allowed after confirmation") {
            val statement = statement()

            statement
                .markPaid(Instant.now())
                .shouldBe(Failure(SettlementErrors.SETTLEMENT_STATEMENT_INVALID_STATE))
            statement.confirm()
            statement.markPaid(Instant.now()).shouldBe(Success(Unit))
            statement.status shouldBe SettlementStatementStatus.PAID
            statement
                .pendingDomainEvents()
                .single()
                .shouldBeInstanceOf<
                    com.jstore.accounting.domain.settlement.event.SettlementPaidEvent
                >()
        }
    })
