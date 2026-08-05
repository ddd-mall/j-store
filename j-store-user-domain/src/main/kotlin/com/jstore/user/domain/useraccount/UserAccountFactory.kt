package com.jstore.user.domain.useraccount

import com.jstore.common.errors.BusinessError
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.command.UserRegisterCMD

/** 用户账号工厂接口 负责创建初始状态的 UserAccount 聚合 */
interface UserAccountFactory {
    fun create(
        cmd: UserRegisterCMD,
        passwordHasher: PasswordHasher,
    ): Result<UserAccount, BusinessError>
}

class UserAccountFactoryImpl(private val snowFlakSequence: SnowFlakSequence) : UserAccountFactory {

    companion object {
        private val PASSWORD_LETTER_REGEX = Regex("[a-zA-Z]")
        private val PASSWORD_DIGIT_REGEX = Regex("[0-9]")

        /** 校验密码强度：8-32 位，至少包含一个字母和一个数字 */
        fun validatePasswordStrength(rawPassword: String): Boolean {
            if (rawPassword.length !in 8..32) return false
            if (!rawPassword.contains(PASSWORD_LETTER_REGEX)) return false
            if (!rawPassword.contains(PASSWORD_DIGIT_REGEX)) return false
            return true
        }
    }

    override fun create(
        cmd: UserRegisterCMD,
        passwordHasher: PasswordHasher,
    ): Result<UserAccount, BusinessError> {
        // 1. 校验密码强度
        if (!validatePasswordStrength(cmd.rawPassword)) {
            return Failure(UserAccountErrors.PASSWORD_STRENGTH_INSUFFICIENT)
        }

        // 2. 校验 Nickname
        val nickname =
            try {
                Nickname(cmd.nickname)
            } catch (e: IllegalArgumentException) {
                return Failure(UserAccountErrors.NICKNAME_INVALID)
            }

        // 3. 哈希密码
        val hashedPassword = passwordHasher.hash(cmd.rawPassword)

        // 4. 生成 UserId
        val userId = UserId(snowFlakSequence.nextId())

        // 5. 创建聚合根
        val userAccount =
            UserAccountImpl(
                id = userId,
                phoneNumber = cmd.phoneNumber,
                nickname = nickname,
                passwordHash = Password(hashedPassword),
                status = UserAccountStatus.ACTIVE,
            )

        // 6. 记录领域事件
        userAccount.recordRegistered()

        return Success(userAccount)
    }
}
