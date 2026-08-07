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

/** 通用行政区划层级，Level 0 = 国家级，Level 1 = 最高行政区划，依次递增 */
data class DivisionLevel(val depth: Int, val name: String) {
    init {
        require(depth >= 0) { "Division level depth must be non-negative" }
    }
}

/** 国家行政区划层级配置 */
data class DivisionLevelConfig(
    val countryCode: CountryCode,
    val levels: List<DivisionLevel>,
) {
    init {
        require(levels.isNotEmpty()) { "Division levels must not be empty" }
    }

    val maxDepth: Int
        get() = levels.maxOf { it.depth }
}
