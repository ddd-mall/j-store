package com.jstore.order.domain.order

import com.jstore.common.properties.PhoneNumber

/**
 * 用户信息值对象
 * 不可变，代表订单购买者的基本信息
 */
data class UserInfo(
    val uid: Long,
    val phoneNumber: PhoneNumber?,      // ✅ 改为 val（不可变）
    val userName: String?                // ✅ 改为 val（不可变）
) {
    init {
        require(uid > 0) { "用户ID必须大于0" }
    }
}
