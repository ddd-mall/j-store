package com.jstore.accounting.domain.account

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AgreeGate
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

interface LedgerAccount : AgreeGate<LedgerAccountId> {
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
