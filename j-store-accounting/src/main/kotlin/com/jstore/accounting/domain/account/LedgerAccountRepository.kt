package com.jstore.accounting.domain.account

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.Repository
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success

interface LedgerAccountRepository : Repository<LedgerAccountId, LedgerAccount> {
    fun findByCodeAndSubject(code: LedgerAccountCode, subject: AccountingSubject): LedgerAccount?

    fun requireActive(id: LedgerAccountId): Result<LedgerAccount, BusinessError> {
        val account = findById(id) ?: return Failure(AccountingAccountErrors.ACCOUNT_NOT_FOUND)
        if (account.status != LedgerAccountStatus.ACTIVE) {
            return Failure(AccountingAccountErrors.LEDGER_ACCOUNT_INACTIVE)
        }
        return Success(account)
    }
}
