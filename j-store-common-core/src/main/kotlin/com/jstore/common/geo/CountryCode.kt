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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * ISO 3166-1 alpha-2 国家编码值对象 不可变，封装国家编码验证逻辑
 *
 * Jackson: 序列化为纯字符串 "CN"，而非 {"value":"CN"}
 */
data class CountryCode
@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
constructor(@get:JsonValue val value: String) {
    init {
        require(value.length == 2 && value.all { it.isUpperCase() }) {
            "CountryCode must be ISO 3166-1 alpha-2 format: $value"
        }
    }

    companion object {
        val CN = CountryCode("CN")
        val US = CountryCode("US")
        val JP = CountryCode("JP")
        val SG = CountryCode("SG")
    }
}
