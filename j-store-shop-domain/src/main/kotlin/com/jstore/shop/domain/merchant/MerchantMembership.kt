package com.jstore.shop.domain.merchant

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.LocalDateTime

class MerchantMembership(
    override val id: MerchantMembershipId,
    val merchantId: MerchantId,
    val userId: Long,
    roles: Set<MerchantRole>,
    status: MerchantMembershipStatus = MerchantMembershipStatus.ACTIVE,
    val createTime: LocalDateTime = LocalDateTime.now(),
    updateTime: LocalDateTime = LocalDateTime.now(),
) : AggregateRoot<MerchantMembershipId> {
    private var _roles: Set<MerchantRole> = roles.toSet()
    private var _status: MerchantMembershipStatus = status
    private var _updateTime: LocalDateTime = updateTime

    val roles: Set<MerchantRole>
        get() = _roles

    val status: MerchantMembershipStatus
        get() = _status

    val updateTime: LocalDateTime
        get() = _updateTime

    init {
        require(userId > 0) { "userId must be positive" }
        require(_roles.isNotEmpty()) { "membership roles must not be empty" }
    }

    fun allows(permission: MerchantPermission): Boolean =
        _status == MerchantMembershipStatus.ACTIVE && _roles.any { permission in it.permissions }

    fun changeRoles(newRoles: Set<MerchantRole>): Result<Unit, BusinessError> {
        if (MerchantRole.OWNER in _roles) return Failure(MerchantErrors.OWNER_PROTECTED)
        if (newRoles.isEmpty()) return Failure(MerchantErrors.ROLES_EMPTY)
        if (MerchantRole.OWNER in newRoles) return Failure(MerchantErrors.OWNER_ROLE_RESERVED)
        _roles = newRoles.toSet()
        _updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    fun disable(): Result<Unit, BusinessError> {
        if (MerchantRole.OWNER in _roles) return Failure(MerchantErrors.OWNER_PROTECTED)
        if (_status != MerchantMembershipStatus.ACTIVE) return Failure(MerchantErrors.ILLEGAL_STATE)
        _status = MerchantMembershipStatus.DISABLED
        _updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    fun enable(): Result<Unit, BusinessError> {
        if (_status != MerchantMembershipStatus.DISABLED)
            return Failure(MerchantErrors.ILLEGAL_STATE)
        _status = MerchantMembershipStatus.ACTIVE
        _updateTime = LocalDateTime.now()
        return Success(Unit)
    }
}
