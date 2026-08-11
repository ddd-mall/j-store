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

import com.jstore.shop.api.OfferSnapshotQueryService

class OfferServiceImpl(private val queryService: OfferSnapshotQueryService) : OfferService {
    override fun queryOffers(offerIds: List<Long>): List<OfferInfo> =
        queryService.queryOffers(offerIds).map {
            OfferInfo(
                offerId = it.offerId,
                storeId = it.storeId,
                merchantId = it.merchantId,
                skuId = it.skuId,
                channelId = it.channelId,
                market = it.market,
                price = it.price,
                version = it.offerVersion,
                fulfillmentNodeId = it.fulfillmentNodeId,
                allowBackorder = it.allowBackorder,
            )
        }
}
