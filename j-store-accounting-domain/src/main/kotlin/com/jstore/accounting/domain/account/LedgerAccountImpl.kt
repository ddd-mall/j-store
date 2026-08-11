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
import com.jstore.common.framework.EventRecordingAggregateRoot
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success

class LedgerAccountImpl(
    override val id: LedgerAccountId,
    override val code: LedgerAccountCode,
    override val name: String,
    override val type: LedgerAccountType,
    override val direction: BalanceDirection,
    override val subject: AccountingSubject,
    private var _status: LedgerAccountStatus,
) : EventRecordingAggregateRoot<LedgerAccountId>(), LedgerAccount {

    init {
        require(name.isNotBlank()) { "账务账户名称不能为空" }
    }

    override val status: LedgerAccountStatus
        get() = _status

    override fun deactivate(): Result<Unit, BusinessError> {
        _status = LedgerAccountStatus.INACTIVE
        return Success(Unit)
    }

    override fun activate(): Result<Unit, BusinessError> {
        _status = LedgerAccountStatus.ACTIVE
        return Success(Unit)
    }
}
