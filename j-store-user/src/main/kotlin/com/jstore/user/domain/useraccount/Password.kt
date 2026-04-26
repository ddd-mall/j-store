package com.jstore.user.domain.useraccount

data class Password(val hashedValue: String) {
    init {
        require(hashedValue.isNotBlank()) { "密码哈希不能为空" }
    }
}
