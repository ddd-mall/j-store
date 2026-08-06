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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * 国际化电话号码值对象
 *
 * 规范存储为 E.164 格式（如 +8613800138000、+14155552671），使用 libphonenumber 按号码所属国家/地区校验有效性。
 * 构造入参必须是不含空格与分隔符的规范 E.164 字符串；区号与国内号码通过派生属性访问。
 */
data class PhoneNumber
@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
constructor(@get:JsonValue val value: String) {

    /** 国家区号（不含 +），如 86、1、81 */
    val countryCallingCode: Int

    /** 去除区号后的国内有效号码部分 */
    val nationalNumber: String

    init {
        require(value.startsWith("+")) {
            "Phone number must be in E.164 format starting with '+': $value"
        }
        val parsed =
            try {
                UTIL.parse(value, null)
            } catch (e: NumberParseException) {
                throw IllegalArgumentException("Cannot parse phone number: $value", e)
            }
        require(UTIL.isValidNumber(parsed)) { "Phone number is not valid for its region: $value" }
        require(value == UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)) {
            "Phone number must be canonical E.164 without spaces or separators: $value"
        }
        countryCallingCode = parsed.countryCode
        nationalNumber = UTIL.getNationalSignificantNumber(parsed)
    }

    companion object {
        private val UTIL: PhoneNumberUtil = PhoneNumberUtil.getInstance()

        /** 由国家区号与国内号码构造，如 of(86, "13800138000") */
        fun of(countryCallingCode: Int, nationalNumber: String): PhoneNumber =
            PhoneNumber("+$countryCallingCode$nationalNumber")
    }
}
