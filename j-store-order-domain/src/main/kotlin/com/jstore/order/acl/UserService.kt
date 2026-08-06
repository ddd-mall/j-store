package com.jstore.order.acl

import com.jstore.order.domain.order.UserInfo

fun interface UserService {
    fun findUserInfo(userId: Long): UserInfo?
}
