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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.util.Locale

/** 地址组件：一个行政区划节点 包含编码、层级、多语言名称映射 */
data class AddressComponent(
    val code: String,
    val level: DivisionLevel,
    @param:JsonSerialize(keyUsing = LocaleKeySerializer::class)
    @field:JsonSerialize(keyUsing = LocaleKeySerializer::class)
    @get:JsonSerialize(keyUsing = LocaleKeySerializer::class)
    @param:JsonDeserialize(keyUsing = LocaleKeyDeserializer::class)
    @field:JsonDeserialize(keyUsing = LocaleKeyDeserializer::class)
    @get:JsonDeserialize(keyUsing = LocaleKeyDeserializer::class)
    val names: Map<Locale, String>,
    @param:JsonSerialize(using = LocaleSerializer::class)
    @field:JsonSerialize(using = LocaleSerializer::class)
    @get:JsonSerialize(using = LocaleSerializer::class)
    @param:JsonDeserialize(using = LocaleDeserializer::class)
    @field:JsonDeserialize(using = LocaleDeserializer::class)
    @get:JsonDeserialize(using = LocaleDeserializer::class)
    val defaultLocale: Locale,
) {
    init {
        require(code.isNotBlank()) { "Address component code must not be blank" }
        require(names.isNotEmpty()) { "Address component must have at least one locale name" }
        require(names.containsKey(defaultLocale)) {
            "Default locale $defaultLocale must exist in names map"
        }
    }

    /** 获取指定 Locale 的名称，不存在则回退到默认 Locale */
    fun getName(locale: Locale): String = names[locale] ?: names.getValue(defaultLocale)

    /** 获取默认 Locale 的名称 */
    @JsonIgnore fun getDefaultName(): String = names.getValue(defaultLocale)
}
