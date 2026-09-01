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
package com.jstore.cart.api

data class CartCheckoutSourceQuery(
    val buyerId: Long,
    val cartId: Long,
    val expectedCartVersion: Long,
)

data class CartCheckoutLine(
    val cartLineId: Long,
    val offerId: Long,
    val offerVersion: Long,
    val spuId: Long,
    val skuId: Long,
    val quantity: Int,
    val catalogSnapshotVersion: Long,
)

data class CartCheckoutSource(
    val cartId: Long,
    val cartVersion: Long,
    val cartDigest: String,
    val market: String,
    val channelId: String,
    val currency: String,
    val eligibleLines: List<CartCheckoutLine>,
)

sealed interface CartCheckoutSourceResult {
    data class Found(val source: CartCheckoutSource) : CartCheckoutSourceResult
    data object NotFound : CartCheckoutSourceResult
    data object VersionConflict : CartCheckoutSourceResult
    data object NoEligibleLines : CartCheckoutSourceResult
    data object Unavailable : CartCheckoutSourceResult
}

fun interface CartCheckoutSourceQueryService {
    fun prepare(query: CartCheckoutSourceQuery): CartCheckoutSourceResult
}
