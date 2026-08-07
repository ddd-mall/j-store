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

// Feature: geo-address-i18n, Property 7: 不支持的国家编码错误
import com.jstore.common.errors.BusinessError
import com.jstore.common.geo.chinese.ChinaAddressProvider
import com.jstore.common.utils.Failure
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property 7: 不支持的国家编码错误
 *
 * For any valid CountryCode that is NOT "CN", calling getByCode on a GeoAddressServiceProxy with
 * only ChinaAddressProvider registered returns Failure with error code
 * `Address.Country.Unsupported`.
 *
 * **Validates: Requirements 2.6, 6.4**
 */
class GeoAddressServiceProxyPropertyTest :
    FunSpec({
        val proxy = GeoAddressServiceProxy(listOf(ChinaAddressProvider()))

        /** Generates random 2-uppercase-letter strings that are not "CN" */
        fun arbNonCnCountryCode(): Arb<String> = arbitrary {
            var code: String
            do {
                val c1 = Arb.char('A'..'Z').bind()
                val c2 = Arb.char('A'..'Z').bind()
                code = "$c1$c2"
            } while (code == "CN")
            code
        }

        test(
            "Property 7: unsupported country code returns Failure with Address.Country.Unsupported"
        ) {
            // Feature: geo-address-i18n, Property 7: 不支持的国家编码错误
            checkAll(100, arbNonCnCountryCode(), Arb.string(1..10)) { countryCode, addressCode ->
                val result = proxy.getByCode(countryCode, addressCode)
                result.shouldBeInstanceOf<Failure<*>>()
                val error = (result as Failure).error as BusinessError
                error.errorCode shouldBe "Address.Country.Unsupported"
            }
        }
    })
