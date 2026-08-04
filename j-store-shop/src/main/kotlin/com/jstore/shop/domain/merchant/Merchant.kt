package com.jstore.shop.domain.merchant

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AgreeGate
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.LocalDateTime
import java.util.LinkedList
import java.util.Queue

class Merchant(
    override val id: MerchantId,
    name: String,
    status: MerchantStatus = MerchantStatus.ACTIVE,
    val createTime: LocalDateTime = LocalDateTime.now(),
    updateTime: LocalDateTime = LocalDateTime.now(),
) : AgreeGate<MerchantId> {
    override val domainEventQueue: Queue<DomainEvent> = LinkedList()

    var name: String = name.trim()
        private set

    var status: MerchantStatus = status
        private set

    var updateTime: LocalDateTime = updateTime
        private set

    init {
        require(validName(this.name)) { "merchant name must contain 1 to 128 characters" }
    }

    fun rename(newName: String): Result<Unit, BusinessError> {
        val normalized = newName.trim()
        if (!validName(normalized)) return Failure(MerchantErrors.NAME_INVALID)
        name = normalized
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    fun disable(): Result<Unit, BusinessError> {
        if (status != MerchantStatus.ACTIVE) return Failure(MerchantErrors.ILLEGAL_STATE)
        status = MerchantStatus.DISABLED
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    fun enable(): Result<Unit, BusinessError> {
        if (status != MerchantStatus.DISABLED) return Failure(MerchantErrors.ILLEGAL_STATE)
        status = MerchantStatus.ACTIVE
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    companion object {
        fun validName(name: String): Boolean = name.isNotBlank() && name.length <= 128
    }
}
