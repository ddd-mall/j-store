package com.jstore.user

import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Feature: user-account, Property 6: 昵称修改生效
 *
 * For any ACTIVE 状态的 UserAccount 和任意合法的新 Nickname，
 * 执行 changeNickname 后，UserAccount 的 nickname 应等于新值。
 *
 * **Validates: Requirements 5.1**
 */
class UserAccountChangeNicknamePropertyTest : FunSpec({

    fun arbActiveUserAccount(): Arb<UserAccountImpl> =
        Arb.long(1L..999_999L).map { id ->
            UserAccountImpl(
                id = UserId(id),
                phoneNumber = PhoneNumber("13800138000"),
                nickname = Nickname("user$id"),
                passwordHash = Password("hashed_password"),
                status = UserAccountStatus.ACTIVE,
            )
        }

    fun arbValidNickname(): Arb<Nickname> =
        Arb.string(1..20).filter { it.isNotBlank() }.map { Nickname(it) }

    test("changeNickname on ACTIVE account should succeed and nickname equals new value") {
        checkAll(100, arbActiveUserAccount(), arbValidNickname()) { account, newNickname ->
            val result = account.changeNickname(newNickname)
            result.shouldBeInstanceOf<Success<Unit>>()
            account.nickname shouldBe newNickname
        }
    }
})
