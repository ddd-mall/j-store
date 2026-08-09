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
package com.jstore.common.properties

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * PhoneNumber 国际化值对象属性测试
 *
 * For any 合法的 E.164 号码，PhoneNumber 应接受并正确拆分国家区号与国内号码； 非 E.164 格式、非规范形式或不符合所属国家号码规则的输入应被拒绝。
 */
class PhoneNumberPropertyTest :
    FunSpec({

        // Generator for valid Chinese mobile national numbers starting with 13x
        val cnMobileArb: Arb<String> =
            Arb.int(0..99999999).map { num -> "13${num.toString().padStart(9, '0')}" }

        test(
            "valid CN mobile numbers in E.164 are accepted and split into calling code + national number"
        ) {
            checkAll(100, cnMobileArb) { national ->
                val phone = PhoneNumber("+86$national")
                phone.countryCallingCode shouldBe 86
                phone.nationalNumber shouldBe national
                phone.value shouldBe "+86$national"
            }
        }

        test("valid numbers from multiple regions are accepted with correct calling code") {
            val samples =
                listOf(
                    "+14155552671" to 1, // US
                    "+818012345678" to 81, // JP
                    "+447911123456" to 44, // GB
                    "+82212345678" to 82, // KR
                )
            samples.forEach { (value, expectedCallingCode) ->
                val phone = PhoneNumber(value)
                phone.countryCallingCode shouldBe expectedCallingCode
                phone.value shouldBe value
            }
        }

        test("numbers without leading '+' are rejected") {
            checkAll(100, cnMobileArb) { national ->
                shouldThrow<IllegalArgumentException> { PhoneNumber(national) }
                shouldThrow<IllegalArgumentException> { PhoneNumber("86$national") }
            }
        }

        test("non-canonical E.164 with separators is rejected") {
            shouldThrow<IllegalArgumentException> { PhoneNumber("+86 138 0013 8000") }
            shouldThrow<IllegalArgumentException> { PhoneNumber("+86-13800138000") }
        }

        test("structurally parseable but region-invalid numbers are rejected") {
            // 国内号码位数不足的中国号码
            shouldThrow<IllegalArgumentException> { PhoneNumber("+861380013800") }
            // 中国不存在 12 开头的手机号段
            shouldThrow<IllegalArgumentException> { PhoneNumber("+8612800138000") }
        }

        test("of() composes the same value object as the E.164 constructor") {
            checkAll(100, cnMobileArb) { national ->
                PhoneNumber.of(86, national) shouldBe PhoneNumber("+86$national")
            }
        }
    })
