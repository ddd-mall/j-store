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
package com.jstore.cart.config

import com.jstore.cart.domain.CartLimits
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jstore.cart.limits")
class CartLimitsProperties {
    var minQuantity: Int = 1
    var maxQuantity: Int = 999
    var maxLines: Int = 100

    fun toLimits() = CartLimits(minQuantity, maxQuantity, maxLines)
}

@ConfigurationProperties("jstore.cart.nacos")
class CartNacosProperties {
    var enabled: Boolean = false
    var serverAddr: String = ""
    var namespace: String = "public"
    var group: String = "DEFAULT_GROUP"
    var dataId: String = "j-store-cart.properties"
    var timeoutMs: Long = 3000
    var username: String = ""
    var password: String = ""
}
