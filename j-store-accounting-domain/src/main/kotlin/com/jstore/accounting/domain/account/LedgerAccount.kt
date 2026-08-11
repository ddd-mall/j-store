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
package com.jstore.accounting.domain.account

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.framework.RecordsDomainEvents
import com.jstore.common.properties.Id
import com.jstore.common.utils.Result

data class LedgerAccountId(override val value: Long) : Id<Long>(value)

data class LedgerAccountCode(val value: String) {
    init {
        require(value.isNotBlank()) { "账务账户编码不能为空" }
    }
}

data class AccountingSubject(
    val subjectType: SubjectType,
    val subjectId: String,
) {
    init {
        require(subjectId.isNotBlank()) { "账务主体ID不能为空" }
    }
}

enum class LedgerAccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE,
}

enum class BalanceDirection {
    DEBIT,
    CREDIT,
}

enum class LedgerAccountStatus {
    ACTIVE,
    INACTIVE,
}

enum class SubjectType {
    PLATFORM,
    MERCHANT,
    USER,
    CHANNEL,
}

interface LedgerAccount : AggregateRoot<LedgerAccountId>, RecordsDomainEvents {
    override val id: LedgerAccountId
    val code: LedgerAccountCode
    val name: String
    val type: LedgerAccountType
    val direction: BalanceDirection
    val subject: AccountingSubject
    val status: LedgerAccountStatus

    fun deactivate(): Result<Unit, BusinessError>

    fun activate(): Result<Unit, BusinessError>
}
