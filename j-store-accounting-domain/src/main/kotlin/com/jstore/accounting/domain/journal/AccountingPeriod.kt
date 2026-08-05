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
import com.jstore.common.framework.AgreeGate
import com.jstore.common.properties.Id
import com.jstore.common.utils.Result
import java.time.Instant
import java.time.LocalDate

data class AccountingPeriodId(override val value: Long) : Id<Long>(value)

enum class PeriodStatus {
    OPEN,
    CLOSED,
}

interface AccountingPeriod : AgreeGate<AccountingPeriodId> {
    override val id: AccountingPeriodId
    val periodCode: String
    val startDate: LocalDate
    val endDate: LocalDate
    val status: PeriodStatus
    val closedAt: Instant?
    val closedBy: String?

    fun contains(date: LocalDate): Boolean

    fun close(closedBy: String): Result<Unit, BusinessError>

    fun reopen(reason: String): Result<Unit, BusinessError>
}
