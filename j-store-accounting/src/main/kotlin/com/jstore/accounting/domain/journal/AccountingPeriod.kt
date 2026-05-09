package com.jstore.accounting.domain.journal

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AgreeGate
import com.jstore.common.properties.Id
import com.jstore.common.utils.Result
import java.time.Instant
import java.time.LocalDate

data class AccountingPeriodId(override val value: Long) : Id<Long>(value)

enum class PeriodStatus { OPEN, CLOSED }

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
