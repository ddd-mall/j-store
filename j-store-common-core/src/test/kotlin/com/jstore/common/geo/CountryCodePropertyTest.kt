package com.jstore.common.geo

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

// Feature: geo-address-i18n, Property 2: CountryCode 验证
/**
 * Property 2: CountryCode 验证
 *
 * For any 字符串，CountryCode 构造应当且仅当该字符串恰好为2个大写字母时成功；
 * 其他任何输入都应被拒绝。
 *
 * **Validates: Requirements 1.2**
 */
class CountryCodePropertyTest : FunSpec({

    val isValidCountryCode: (String) -> Boolean = { s ->
        s.length == 2 && s.all { it in 'A'..'Z' }
    }

    test("valid 2-uppercase-letter strings construct CountryCode successfully") {
        val arbValid: Arb<String> = Arb.char('A'..'Z').flatMap { c1 ->
            Arb.char('A'..'Z').map { c2 -> "$c1$c2" }
        }
        checkAll(100, arbValid) { s ->
            val cc = CountryCode(s)
            cc.value shouldBe s
        }
    }

    test("strings that are not exactly 2 uppercase letters are rejected") {
        val arbInvalid: Arb<String> = Arb.string(0..10).filter { !isValidCountryCode(it) }
        checkAll(100, arbInvalid) { s ->
            shouldThrow<IllegalArgumentException> { CountryCode(s) }
        }
    }
})
