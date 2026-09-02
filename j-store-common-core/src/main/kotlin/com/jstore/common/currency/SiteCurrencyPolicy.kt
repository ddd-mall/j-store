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
package com.jstore.common.currency

/** Deployment-level currency selection; domain aggregates must not depend on this policy. */
class SiteCurrencyPolicy(
    defaultCurrency: String,
    allowedCurrencies: Set<String>,
) {
    val defaultCurrency: String = defaultCurrency.trim()
    val allowedCurrencies: Set<String> = allowedCurrencies.mapTo(linkedSetOf()) { it.trim() }

    init {
        require(CurrencyCode.isValid(this.defaultCurrency)) {
            "site default currency must be a valid ISO 4217 code"
        }
        require(this.allowedCurrencies.isNotEmpty()) { "site allowed currencies must not be empty" }
        require(this.allowedCurrencies.all(CurrencyCode::isValid)) {
            "site allowed currencies must be valid ISO 4217 codes"
        }
        require(this.defaultCurrency in this.allowedCurrencies) {
            "site default currency must be allowed"
        }
    }

    /**
     * Returns the selected site currency, or null when an explicit value is invalid/unsupported.
     */
    fun select(requestedCurrency: String?): String? {
        val selected = requestedCurrency ?: defaultCurrency
        return selected.takeIf { CurrencyCode.isValid(it) && it in allowedCurrencies }
    }
}
