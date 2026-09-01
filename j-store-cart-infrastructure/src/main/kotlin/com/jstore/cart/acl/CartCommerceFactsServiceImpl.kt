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
package com.jstore.cart.acl

import com.jstore.cart.domain.*
import com.jstore.goods.api.CurrentGoodsSkuQueryService
import com.jstore.inventory.api.InventoryAvailabilityKey
import com.jstore.inventory.api.InventoryAvailabilityQueryService
import com.jstore.shop.api.OfferSnapshotQueryService
import java.time.Clock

class CartCommerceFactsServiceImpl(
    private val goods: CurrentGoodsSkuQueryService,
    private val offers: OfferSnapshotQueryService,
    private val inventory: InventoryAvailabilityQueryService,
    private val clock: Clock = Clock.systemUTC(),
) : CartCommerceFactsService {
    override fun findOffer(offerId: OfferId): OfferIdentity? =
        offers.queryOffers(listOf(offerId.value)).singleOrNull()?.let {
            OfferIdentity(
                offerId,
                SkuId(it.skuId),
                it.merchantId,
                SettlementScope(it.market, it.channelId, it.currency),
            )
        }

    override fun collect(lines: List<CartLine>): List<CartLineCommerceFacts> {
        val offerById =
            offers.queryOffers(lines.map { it.offerId.value }.distinct()).associateBy { it.offerId }
        val goodsBySku =
            goods.querySkus(lines.map { it.skuId.value }.distinct()).associateBy { it.skuId }
        val keys =
            lines
                .mapNotNull { line ->
                    offerById[line.offerId.value]?.let {
                        InventoryAvailabilityKey(line.skuId.value, it.fulfillmentNodeId)
                    }
                }
                .distinct()
        val atpByKey =
            inventory.queryAvailability(keys).associateBy { it.skuId to it.fulfillmentNodeId }
        val now = clock.instant()
        return lines.map { line ->
            val offer = offerById[line.offerId.value]
            val catalog = goodsBySku[line.skuId.value]
            val atp = offer?.let { atpByKey[line.skuId.value to it.fulfillmentNodeId] }
            CartLineCommerceFacts(
                line.id,
                catalog?.published == true,
                offer != null &&
                    offer.skuId == line.skuId.value &&
                    offer.merchantId == line.merchantId.value &&
                    offer.active &&
                    offer.storeActive &&
                    offer.effectiveNow &&
                    !now.isBefore(offer.startsAt) &&
                    (offer.endsAt == null || now.isBefore(offer.endsAt)),
                offer?.price,
                offer?.offerVersion,
                catalog?.catalogSnapshotVersion,
                atp?.availableToPromise,
                catalog?.spuId,
                offer?.fulfillmentNodeId,
                offer?.market,
                offer?.channelId,
                offer?.currency,
                offer?.merchantId,
                catalog?.goodsName,
                catalog?.skuDescription,
            )
        }
    }
}
