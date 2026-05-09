package com.jstore.user.domain.useraccount

data class Nickname(val value: String) {
    init {
        require(value.isNotBlank()) { "昵称不能为空" }
        require(value.length <= 20) { "昵称长度不能超过20个字符" }
    }
}
