package com.jstore.accounting.domain.journal

import com.jstore.accounting.domain.account.LedgerAccountId
import com.jstore.common.properties.Price
import java.time.LocalDate

data class AccountingBalanceView(
    val accountId: LedgerAccountId,
    val debitAmount: Price,
    val creditAmount: Price,
    val balance: Price,
)

data class AccountingBalanceQuery(
    val accountId: LedgerAccountId? = null,
    val subjectId: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)
