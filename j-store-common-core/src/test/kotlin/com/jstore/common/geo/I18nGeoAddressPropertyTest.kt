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

// Feature: geo-address-i18n, Property 1: 值对象构造不变量
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveMinLength
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.util.Locale

/**
 * Property 1: 值对象构造不变量
 *
 * For any legal I18nGeoAddress, its countryCode must be a valid ISO 3166-1 alpha-2 code, components
 * list must be non-empty, and each AddressComponent's code must be non-blank, names must be
 * non-empty, and defaultLocale must exist in the names map.
 *
 * **Validates: Requirements 1.1, 1.3, 1.4, 3.1, 3.4**
 */
class I18nGeoAddressPropertyTest :
    FunSpec({
        val supportedLocales =
            listOf(
                Locale.SIMPLIFIED_CHINESE,
                Locale.US,
                Locale.JAPAN,
                Locale.KOREA,
                Locale.FRANCE,
                Locale.GERMANY,
                Locale.UK,
                Locale.ITALY,
                Locale.CANADA,
                Locale.TRADITIONAL_CHINESE,
            )

        val validCountryCodes =
            listOf(CountryCode.CN, CountryCode.US, CountryCode.JP, CountryCode.SG)

        /** Custom Arb that generates an AddressComponent with random multi-locale names */
        fun Arb.Companion.addressComponent(): Arb<AddressComponent> {
            return Arb.int(1..5).flatMap { nameCount ->
                val count = nameCount.coerceAtMost(supportedLocales.size)
                Arb.list(Arb.element(supportedLocales), count..count)
                    .map { it.distinct() }
                    .filter { it.isNotEmpty() }
                    .flatMap { locales ->
                        Arb.list(Arb.string(1..20), locales.size..locales.size).flatMap { names ->
                            val namesMap = locales.zip(names).toMap()
                            Arb.element(locales).flatMap { defaultLocale ->
                                Arb.string(1..10)
                                    .filter { it.isNotBlank() }
                                    .flatMap { code ->
                                        Arb.int(0..5).map { depth ->
                                            AddressComponent(
                                                code = code,
                                                level = DivisionLevel(depth, "Level$depth"),
                                                names = namesMap,
                                                defaultLocale = defaultLocale,
                                            )
                                        }
                                    }
                            }
                        }
                    }
            }
        }

        /** Custom Arb that generates a valid I18nGeoAddress */
        fun Arb.Companion.i18nGeoAddress(): Arb<I18nGeoAddress> {
            return Arb.element(validCountryCodes).flatMap { countryCode ->
                Arb.list(Arb.addressComponent(), 1..4).map { components ->
                    I18nGeoAddress(
                        countryCode = countryCode,
                        components = components,
                    )
                }
            }
        }

        // Feature: geo-address-i18n, Property 1: 值对象构造不变量
        test("countryCode is valid ISO 3166-1 alpha-2 for all legal instances") {
            checkAll(100, Arb.i18nGeoAddress()) { address ->
                address.countryCode.value.length shouldBe 2
                address.countryCode.value.all { it.isUpperCase() } shouldBe true
            }
        }

        test("components list is non-empty for all legal instances") {
            checkAll(100, Arb.i18nGeoAddress()) { address ->
                address.components.isNotEmpty() shouldBe true
            }
        }

        test(
            "each component code is non-blank, names is non-empty, defaultLocale exists in names"
        ) {
            checkAll(100, Arb.i18nGeoAddress()) { address ->
                for (component in address.components) {
                    component.code shouldHaveMinLength 1
                    component.code.isNotBlank() shouldBe true
                    component.names.isNotEmpty() shouldBe true
                    component.names.containsKey(component.defaultLocale) shouldBe true
                }
            }
        }
    })
