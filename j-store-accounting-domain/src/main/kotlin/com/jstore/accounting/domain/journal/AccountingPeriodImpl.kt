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
import com.jstore.common.framework.EventRecordingAggregateRoot
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.Instant
import java.time.LocalDate

class AccountingPeriodImpl(
    override val id: AccountingPeriodId,
    override val periodCode: String,
    override val startDate: LocalDate,
    override val endDate: LocalDate,
    private var _status: PeriodStatus,
    private var _closedAt: Instant? = null,
    private var _closedBy: String? = null,
) : EventRecordingAggregateRoot<AccountingPeriodId>(), AccountingPeriod {

    init {
        require(periodCode.isNotBlank()) { "会计期间编码不能为空" }
        require(!startDate.isAfter(endDate)) { "会计期间开始日期不能晚于结束日期" }
    }

    override val status: PeriodStatus
        get() = _status

    override val closedAt: Instant?
        get() = _closedAt

    override val closedBy: String?
        get() = _closedBy

    override fun contains(date: LocalDate): Boolean =
        !date.isBefore(startDate) && !date.isAfter(endDate)

    override fun close(closedBy: String): Result<Unit, BusinessError> {
        if (closedBy.isBlank()) {
            return Failure(AccountingErrors.JOURNAL_ENTRY_INVALID_STATE.msg("结账人不能为空"))
        }
        _status = PeriodStatus.CLOSED
        _closedAt = Instant.now()
        _closedBy = closedBy
        return Success(Unit)
    }

    override fun reopen(reason: String): Result<Unit, BusinessError> {
        if (reason.isBlank()) {
            return Failure(AccountingErrors.JOURNAL_ENTRY_INVALID_STATE.msg("反结账原因不能为空"))
        }
        _status = PeriodStatus.OPEN
        _closedAt = null
        _closedBy = null
        return Success(Unit)
    }
}
