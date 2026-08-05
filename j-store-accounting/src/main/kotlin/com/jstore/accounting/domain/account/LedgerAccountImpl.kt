package com.jstore.accounting.domain.account

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.util.LinkedList
import java.util.Queue

class LedgerAccountImpl(
    override val id: LedgerAccountId,
    override val code: LedgerAccountCode,
    override val name: String,
    override val type: LedgerAccountType,
    override val direction: BalanceDirection,
    override val subject: AccountingSubject,
    private var _status: LedgerAccountStatus,
) : LedgerAccount {
    override val domainEventQueue: Queue<DomainEvent> = LinkedList()

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
