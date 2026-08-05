package com.jstore.user

import com.jstore.user.domain.useraccount.UserAccountFactoryImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Feature: user-account, Property 3: 无效密码拒绝
 *
 * For any 不满足强度要求的字符串（长度不在 8-32 范围内，或不同时包含字母和数字）， 密码强度校验应返回失败。
 *
 * **Validates: Requirements 1.6, 6.4**
 */
class PasswordStrengthPropertyTest :
    FunSpec({

        // Generator: pure letters (no digits), valid length 8-32
        val pureLettersArb: Arb<String> =
            Arb.int(8..32).flatMap { len ->
                Arb.list(Arb.char('a'..'z'), len..len).map { it.joinToString("") }
            }

        // Generator: pure digits (no letters), valid length 8-32
        val pureDigitsArb: Arb<String> =
            Arb.int(8..32).flatMap { len ->
                Arb.list(Arb.char('0'..'9'), len..len).map { it.joinToString("") }
            }

        // Generator: too short (< 8 chars), with both letters and digits
        val tooShortArb: Arb<String> =
            Arb.int(2..7).flatMap { len ->
                // Ensure at least one letter and one digit within the short length
                val letterCount = len - 1
                Arb.bind(
                    Arb.list(Arb.char('a'..'z'), letterCount..letterCount),
                    Arb.char('0'..'9'),
                ) { letters, digit ->
                    (letters + digit).shuffled().joinToString("")
                }
            }

        // Generator: too long (> 32 chars), with both letters and digits
        val tooLongArb: Arb<String> =
            Arb.int(33..60).flatMap { len ->
                val letterCount = len - 1
                Arb.bind(
                    Arb.list(Arb.char('a'..'z'), letterCount..letterCount),
                    Arb.char('0'..'9'),
                ) { letters, digit ->
                    (letters + digit).shuffled().joinToString("")
                }
            }

        test("pure letter passwords should fail strength validation") {
            checkAll(100, pureLettersArb) { password ->
                UserAccountFactoryImpl.validatePasswordStrength(password) shouldBe false
            }
        }

        test("pure digit passwords should fail strength validation") {
            checkAll(100, pureDigitsArb) { password ->
                UserAccountFactoryImpl.validatePasswordStrength(password) shouldBe false
            }
        }

        test("too short passwords should fail strength validation") {
            checkAll(100, tooShortArb) { password ->
                UserAccountFactoryImpl.validatePasswordStrength(password) shouldBe false
            }
        }

        test("too long passwords should fail strength validation") {
            checkAll(100, tooLongArb) { password ->
                UserAccountFactoryImpl.validatePasswordStrength(password) shouldBe false
            }
        }
    })
