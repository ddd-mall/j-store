package com.jstore.order.acl

import com.jstore.common.properties.PhoneNumber
import com.jstore.order.domain.order.UserInfo
import com.jstore.user.api.UserProfileQueryService
import com.jstore.user.api.UserProfileStatus

class UserServiceImpl(private val profiles: UserProfileQueryService) : UserService {
    override fun findUserInfo(userId: Long): UserInfo? {
        val profile = profiles.findById(userId) ?: return null
        if (profile.status != UserProfileStatus.ACTIVE) return null
        return UserInfo(
            uid = profile.userId,
            phoneNumber = PhoneNumber(profile.phoneNumber),
            userName = profile.nickname,
        )
    }
}
