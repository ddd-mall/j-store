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

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class AccountingPeriodUnitTest :
    FunSpec({
        test("period contains dates inclusively and can close or reopen") {
            val period =
                AccountingPeriodImpl(
                    id = AccountingPeriodId(1),
                    periodCode = "202604",
                    startDate = LocalDate.of(2026, 4, 1),
                    endDate = LocalDate.of(2026, 4, 30),
                    _status = PeriodStatus.OPEN,
                )

            period.contains(LocalDate.of(2026, 4, 1)) shouldBe true
            period.contains(LocalDate.of(2026, 4, 30)) shouldBe true
            period.contains(LocalDate.of(2026, 5, 1)) shouldBe false
            period.close("finance").shouldBe(Success(Unit))
            period.status shouldBe PeriodStatus.CLOSED
            period.reopen("adjustment").shouldBe(Success(Unit))
            period.status shouldBe PeriodStatus.OPEN
        }

        test("repository requireOpenPeriod rejects closed period") {
            val period =
                AccountingPeriodImpl(
                    id = AccountingPeriodId(1),
                    periodCode = "202604",
                    startDate = LocalDate.of(2026, 4, 1),
                    endDate = LocalDate.of(2026, 4, 30),
                    _status = PeriodStatus.CLOSED,
                )
            val repo =
                object : AccountingPeriodRepository {
                    override fun save(entity: AccountingPeriod): AccountingPeriod = entity

                    override fun findById(id: AccountingPeriodId): AccountingPeriod? =
                        period.takeIf {
                            it.id == id
                        }

                    override fun findByDate(date: LocalDate): AccountingPeriod? = period.takeIf {
                        it.contains(date)
                    }
                }

            repo
                .requireOpenPeriod(LocalDate.of(2026, 4, 30))
                .shouldBe(Failure(AccountingErrors.ACCOUNTING_PERIOD_CLOSED))
        }
    })
