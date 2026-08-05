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

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.Repository
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.LocalDate

interface AccountingPeriodRepository : Repository<AccountingPeriodId, AccountingPeriod> {
    fun findByDate(date: LocalDate): AccountingPeriod?

    fun requireOpenPeriod(date: LocalDate): Result<AccountingPeriod, BusinessError> {
        val period =
            findByDate(date) ?: return Failure(AccountingErrors.ACCOUNTING_PERIOD_NOT_FOUND)
        if (period.status != PeriodStatus.OPEN) {
            return Failure(AccountingErrors.ACCOUNTING_PERIOD_CLOSED)
        }
        return Success(period)
    }
}
