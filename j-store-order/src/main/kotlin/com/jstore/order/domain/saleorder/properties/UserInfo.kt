package com.jstore.order.domain.saleorder.properties

import com.jstore.common.properties.PhoneNumber

data class UserInfo(
    val uid: Long,
    var phoneNumber: PhoneNumber?,
    var userName: String?,
)