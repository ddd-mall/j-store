package com.jstore.user

import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Feature: user-account, Property 5: 账号状态转移规则
 *
 * For any UserAccount，当状态为 ACTIVE 时执行 disable() 应成功且状态变为 DISABLED； 当状态为 DISABLED 时执行 enable()
 * 应成功且状态变为 ACTIVE。 当状态为 ACTIVE 时执行 enable() 应返回失败； 当状态为 DISABLED 时执行 disable() 应返回失败。
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
 */
class UserAccountStatusTransitionPropertyTest :
    FunSpec({
        fun arbUserAccount(status: UserAccountStatus): Arb<UserAccountImpl> =
            Arb.long(1L..999_999L).map { id ->
                UserAccountImpl(
                    id = UserId(id),
                    phoneNumber = PhoneNumber("13800138000"),
                    nickname = Nickname("user$id"),
                    passwordHash = Password("hashed_password"),
                    status = status,
                )
            }

        test("ACTIVE account disable() should succeed and status becomes DISABLED") {
            checkAll(100, arbUserAccount(UserAccountStatus.ACTIVE)) { account ->
                val result = account.disable()
                result.shouldBeInstanceOf<Success<Unit>>()
                account.status shouldBe UserAccountStatus.DISABLED
            }
        }

        test("DISABLED account enable() should succeed and status becomes ACTIVE") {
            checkAll(100, arbUserAccount(UserAccountStatus.DISABLED)) { account ->
                val result = account.enable()
                result.shouldBeInstanceOf<Success<Unit>>()
                account.status shouldBe UserAccountStatus.ACTIVE
            }
        }

        test("ACTIVE account enable() should return failure (ILLEGAL_STATE)") {
            checkAll(100, arbUserAccount(UserAccountStatus.ACTIVE)) { account ->
                val result = account.enable()
                result.shouldBeInstanceOf<Failure<*>>()
                (result as Failure).error shouldBe UserAccountErrors.ILLEGAL_STATE
            }
        }

        test("DISABLED account disable() should return failure (ILLEGAL_STATE)") {
            checkAll(100, arbUserAccount(UserAccountStatus.DISABLED)) { account ->
                val result = account.disable()
                result.shouldBeInstanceOf<Failure<*>>()
                (result as Failure).error shouldBe UserAccountErrors.ILLEGAL_STATE
            }
        }
    })
