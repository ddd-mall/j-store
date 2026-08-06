package com.jstore.user.api

enum class UserProfileStatus {
    ACTIVE,
    DISABLED,
}

data class UserProfileInfo(
    val userId: Long,
    val nickname: String,
    val phoneNumber: String,
    val status: UserProfileStatus,
) {
    init {
        require(userId > 0) { "userId must be positive" }
        require(nickname.isNotBlank()) { "nickname must not be blank" }
        require(E164_PHONE.matches(phoneNumber)) { "phoneNumber must be canonical E.164" }
    }

    private companion object {
        val E164_PHONE = Regex("^\\+[1-9][0-9]{7,14}$")
    }
}

fun interface UserProfileQueryService {
    fun findById(userId: Long): UserProfileInfo?
}
