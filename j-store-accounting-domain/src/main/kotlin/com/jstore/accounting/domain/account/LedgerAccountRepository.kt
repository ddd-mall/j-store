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
import com.jstore.common.framework.AggregateRepository
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success

interface LedgerAccountRepository : AggregateRepository<LedgerAccountId, LedgerAccount> {
    fun findByCodeAndSubject(code: LedgerAccountCode, subject: AccountingSubject): LedgerAccount?

    fun requireActive(id: LedgerAccountId): Result<LedgerAccount, BusinessError> {
        val account = findById(id) ?: return Failure(AccountingAccountErrors.ACCOUNT_NOT_FOUND)
        if (account.status != LedgerAccountStatus.ACTIVE) {
            return Failure(AccountingAccountErrors.LEDGER_ACCOUNT_INACTIVE)
        }
        return Success(account)
    }
}
