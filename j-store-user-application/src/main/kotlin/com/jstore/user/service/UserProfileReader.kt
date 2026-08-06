package com.jstore.user.service

import com.jstore.user.api.UserProfileInfo
import com.jstore.user.api.UserProfileStatus
import com.jstore.user.domain.useraccount.UserAccountRepository
import com.jstore.user.domain.useraccount.UserAccountStatus
import com.jstore.user.domain.useraccount.UserId

class UserProfileReader(private val repository: UserAccountRepository) {
    fun findById(userId: Long): UserProfileInfo? {
        if (userId <= 0) return null
        return repository.findById(UserId(userId))?.let { account ->
            UserProfileInfo(
                userId = account.id.value,
                nickname = account.nickname.value,
                phoneNumber = account.phoneNumber.value,
                status =
                    when (account.status) {
                        UserAccountStatus.ACTIVE -> UserProfileStatus.ACTIVE
                        UserAccountStatus.DISABLED -> UserProfileStatus.DISABLED
                    },
            )
        }
    }
}
