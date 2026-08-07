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

// Feature: geo-address-i18n, Property 6: 国家特定地址编码验证
import com.jstore.common.geo.chinese.ChinaAddressProvider
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property 6: 国家特定地址编码验证
 *
 * For any string, Chinese validateCode succeeds if and only if the string is all-digit and its
 * length is one of the valid code lengths defined by DIVISION_LEVEL_META. Other strings fail with
 * error code `Address.Code.Invalid`.
 *
 * **Validates: Requirements 5.1, 5.2, 5.3**
 */
class AddressCodeValidationPropertyTest :
    FunSpec({
        val provider = ChinaAddressProvider()
        val validLengths = ChinaAddressProvider.VALID_CODE_LENGTHS

        /** Generates a valid Chinese address code string with one of the valid lengths */
        fun arbValidCode(): Arb<String> = arbitrary {
            val length = validLengths.random()
            val maxVal = "9".repeat(length).toLong()
            val minVal = "1" + "0".repeat(length - 1)
            val num =
                Arb.int(minVal.toInt().coerceAtLeast(0)..maxVal.toInt().coerceAtMost(Int.MAX_VALUE))
                    .bind()
            num.toString().padStart(length, '0')
        }

        test("Property 6: Chinese code validation succeeds for valid-length digit strings") {
            // Feature: geo-address-i18n, Property 6: 国家特定地址编码验证
            checkAll(100, arbValidCode()) { code ->
                val result = provider.validateCode(code)
                result.shouldBeInstanceOf<Success<Unit>>()
                result.value shouldBe Unit
            }
        }

        test(
            "Property 6: Non-valid-length or non-numeric strings fail with Address.Code.Invalid error code"
        ) {
            // Feature: geo-address-i18n, Property 6: 国家特定地址编码验证
            checkAll(100, Arb.string(0..20)) { randomStr ->
                val isValidDigitCode =
                    randomStr.all { it.isDigit() } && randomStr.length in validLengths
                val result = provider.validateCode(randomStr)

                if (isValidDigitCode) {
                    result.shouldBeInstanceOf<Success<Unit>>()
                } else {
                    result.shouldBeInstanceOf<Failure<*>>()
                    val error = (result as Failure).error as com.jstore.common.errors.BusinessError
                    error.errorCode shouldBe "Address.Code.Invalid"
                }
            }
        }
    })
