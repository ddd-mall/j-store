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

/** 通用 i18n 地址值对象 不可变，表达任意国家的行政区划地址 */
data class I18nGeoAddress(
    val countryCode: CountryCode,
    val components: List<AddressComponent>,
) {
    init {
        require(components.isNotEmpty()) { "Address components must not be empty" }
    }

    /** 获取指定层级的组件 */
    fun getComponentAtLevel(depth: Int): AddressComponent? = components.find {
        it.level.depth == depth
    }

    /** 获取最末层级组件的编码（通常用作地址主编码） */
    @JsonIgnore fun getLeafCode(): String = components.last().code
}
