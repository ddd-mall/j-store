package com.jstore.user.domain.useraccount

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.LocalDateTime
import java.util.*

/** UserAccount 聚合根实现 */
class UserAccountImpl(
    override val id: UserId,
    override val phoneNumber: PhoneNumber,
    override var nickname: Nickname,
    override var passwordHash: Password,
    override var status: UserAccountStatus,
    override val createTime: LocalDateTime = LocalDateTime.now(),
    override var updateTime: LocalDateTime = LocalDateTime.now(),
) : UserAccount {

    override val domainEventQueue: Queue<DomainEvent> = LinkedList()

    override fun changeNickname(newNickname: Nickname): Result<Unit, BusinessError> {
        nickname = newNickname
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    override fun changePassword(newPasswordHash: Password): Result<Unit, BusinessError> {
        passwordHash = newPasswordHash
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    override fun disable(): Result<Unit, BusinessError> {
        if (status != UserAccountStatus.ACTIVE) {
            return Failure(UserAccountErrors.ILLEGAL_STATE)
        }
        status = UserAccountStatus.DISABLED
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    override fun enable(): Result<Unit, BusinessError> {
        if (status != UserAccountStatus.DISABLED) {
            return Failure(UserAccountErrors.ILLEGAL_STATE)
        }
        status = UserAccountStatus.ACTIVE
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }
}
