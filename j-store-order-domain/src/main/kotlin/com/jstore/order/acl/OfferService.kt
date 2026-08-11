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
package com.jstore.order.acl

import com.jstore.common.properties.Price

fun interface OfferService {
    fun queryOffers(offerIds: List<Long>): List<OfferInfo>
}

data class OfferInfo(
    val offerId: Long,
    val storeId: Long,
    val merchantId: Long,
    val skuId: Long,
    val channelId: String,
    val market: String,
    val price: Price,
    val version: Long,
    val fulfillmentNodeId: String,
    val allowBackorder: Boolean,
)
