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
