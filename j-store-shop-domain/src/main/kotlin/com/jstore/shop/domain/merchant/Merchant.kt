package com.jstore.shop.domain.merchant

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.LocalDateTime

class Merchant(
    override val id: MerchantId,
    name: String,
    status: MerchantStatus = MerchantStatus.ACTIVE,
    val createTime: LocalDateTime = LocalDateTime.now(),
    updateTime: LocalDateTime = LocalDateTime.now(),
) : AggregateRoot<MerchantId> {
    private var _name: String = name.trim()
    private var _status: MerchantStatus = status
    private var _updateTime: LocalDateTime = updateTime

    val name: String
        get() = _name

    val status: MerchantStatus
        get() = _status

    val updateTime: LocalDateTime
        get() = _updateTime

    init {
        require(validName(_name)) { "merchant name must contain 1 to 128 characters" }
    }

    fun rename(newName: String): Result<Unit, BusinessError> {
        val normalized = newName.trim()
        if (!validName(normalized)) return Failure(MerchantErrors.NAME_INVALID)
        _name = normalized
        _updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    fun disable(): Result<Unit, BusinessError> {
        if (_status != MerchantStatus.ACTIVE) return Failure(MerchantErrors.ILLEGAL_STATE)
        _status = MerchantStatus.DISABLED
        _updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    fun enable(): Result<Unit, BusinessError> {
        if (_status != MerchantStatus.DISABLED) return Failure(MerchantErrors.ILLEGAL_STATE)
        _status = MerchantStatus.ACTIVE
        _updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    companion object {
        fun validName(name: String): Boolean = name.isNotBlank() && name.length <= 128
    }
}
