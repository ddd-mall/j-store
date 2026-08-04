package com.jstore.user

import com.jstore.user.domain.useraccount.Nickname
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Feature: user-account, Property 4: Nickname 值对象校验
 *
 * For any 空白字符串或长度超过 20 个字符的字符串，Nickname 构造应抛出异常（拒绝创建）。 For any 非空且长度 ≤ 20 的字符串，Nickname 构造应成功。
 *
 * **Validates: Requirements 1.7, 5.2**
 */
class NicknamePropertyTest :
    FunSpec({
        test("blank strings should be rejected by Nickname constructor") {
            val blankStrings = Arb.string(0..20).filter { it.isBlank() }
            checkAll(100, blankStrings) { blank ->
                shouldThrow<IllegalArgumentException> {
                    Nickname(blank)
                }
            }
        }

        test("strings longer than 20 characters should be rejected by Nickname constructor") {
            val longStrings = Arb.string(21..100).filter { it.isNotBlank() }
            checkAll(100, longStrings) { long ->
                shouldThrow<IllegalArgumentException> {
                    Nickname(long)
                }
            }
        }

        test("non-blank strings with length <= 20 should construct Nickname successfully") {
            val validStrings = Arb.string(1..20).filter { it.isNotBlank() }
            checkAll(100, validStrings) { valid ->
                val nickname = Nickname(valid)
                assert(nickname.value == valid)
            }
        }
    })
