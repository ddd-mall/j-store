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
