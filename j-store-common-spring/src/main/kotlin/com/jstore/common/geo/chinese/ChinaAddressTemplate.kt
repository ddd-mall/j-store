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
package com.jstore.common.geo.chinese

import com.jstore.common.geo.AddressComponent
import com.jstore.common.geo.AddressTemplate
import java.util.Locale

/** 中国地址格式化模板 中国地址：从大到小排列（省 → 市 → 区/县 → 详细地址），无分隔符直接拼接 */
class ChinaAddressTemplate : AddressTemplate {
    override fun format(
        components: List<AddressComponent>,
        locale: Locale,
    ): String {
        val sorted = components.sortedBy { it.level.depth }
        val parts = sorted.map { it.getName(locale) }
        return parts.filter { it.isNotBlank() }.joinToString("")
    }
}
