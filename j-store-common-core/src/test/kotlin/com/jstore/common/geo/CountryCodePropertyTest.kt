/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.common.geo

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

// Feature: geo-address-i18n, Property 2: CountryCode 验证
/**
 * Property 2: CountryCode 验证
 *
 * For any 字符串，CountryCode 构造应当且仅当该字符串恰好为2个大写字母时成功； 其他任何输入都应被拒绝。
 *
 * **Validates: Requirements 1.2**
 */
class CountryCodePropertyTest :
    FunSpec({
        val isValidCountryCode: (String) -> Boolean = { s ->
            s.length == 2 && s.all { it in 'A'..'Z' }
        }

        test("valid 2-uppercase-letter strings construct CountryCode successfully") {
            val arbValid: Arb<String> =
                Arb.char('A'..'Z').flatMap { c1 ->
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
