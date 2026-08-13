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
package com.jstore.goods.domain.content

import java.util.Locale

/** Localized catalog content with normalized BCP 47 locale keys. */
data class LocalizedText(val values: Map<String, String>) {
    init {
        require(values.isNotEmpty()) { "localized text must contain at least one value" }
        require(values.keys.all { it == normalizeLocale(it) }) {
            "locale keys must be normalized BCP 47 tags"
        }
        require(values.values.all { it.isNotBlank() }) { "localized values must not be blank" }
    }

    operator fun get(locale: String): String? = values[normalizeLocale(locale)]

    fun resolve(locale: String): String = get(locale) ?: values.toSortedMap().values.first()

    companion object {
        fun of(vararg entries: Pair<String, String>): LocalizedText =
            LocalizedText(entries.associate { (locale, value) -> normalizeLocale(locale) to value })

        fun normalizeLocale(locale: String): String {
            val normalized = Locale.forLanguageTag(locale.replace('_', '-')).toLanguageTag()
            require(normalized.isNotBlank() && normalized != "und") { "invalid locale: $locale" }
            return normalized
        }
    }
}
