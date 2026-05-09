package com.jstore.user.domain.useraccount.command

import com.jstore.common.properties.PhoneNumber

data class UserRegisterCMD(
    val phoneNumber: PhoneNumber,
    val nickname: String,
    val rawPassword: String,
)
