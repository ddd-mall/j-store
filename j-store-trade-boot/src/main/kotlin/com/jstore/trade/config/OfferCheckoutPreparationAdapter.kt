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
package com.jstore.trade.config

import com.jstore.common.geo.GeoAddressService
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.goods.api.GoodsSnapshotQueryService
import com.jstore.shop.api.OfferSnapshotQueryService
import com.jstore.trade.domain.TradeBuyerProfileSnapshot
import com.jstore.trade.domain.TradeErrors
import com.jstore.trade.service.CheckoutPreparationGateway
import com.jstore.trade.service.CreateCheckoutCommand
import com.jstore.trade.service.PreparedCheckout
import com.jstore.user.api.UserProfileQueryService
import com.jstore.user.api.UserProfileStatus

/** First pricing/preparation adapter: validates Offer versions and freezes trusted sale facts. */
class OfferCheckoutPreparationAdapter(
    private val offers: OfferSnapshotQueryService,
    private val goods: GoodsSnapshotQueryService,
    private val users: UserProfileQueryService,
    private val addresses: GeoAddressService,
) : CheckoutPreparationGateway {
    override fun prepare(command: CreateCheckoutCommand) =
        offers.queryOffers(command.items.map { it.offerId }.distinct()).let { snapshots ->
            val byId = snapshots.associateBy { it.offerId }
            val goodsBySpu =
                goods.queryLatestSnapshots(command.items.map { it.spuId }.distinct()).associateBy {
                    it.spuId
                }
            val profile = users.findById(command.buyerId)
            val offersValid =
                command.items.all { item ->
                    byId[item.offerId]?.let {
                        it.skuId == item.skuId && it.offerVersion == item.offerVersion && it.active && it.storeActive && it.effectiveNow
                    } == true
                }
            val settlementScopes = snapshots.map { Triple(it.market, it.channelId, it.currency) }.distinct()
            val goodsValid =
                command.items.all { item ->
                    val offer = byId[item.offerId]
                    goodsBySpu[item.spuId]?.let { snapshot ->
                        snapshot.snapshotVersion == item.catalogSnapshotVersion &&
                            snapshot.merchantId == offer?.merchantId &&
                            snapshot.skuSnapshots.any { it.skuId == item.skuId }
                    } == true
                }
            if (!offersValid || !goodsValid || settlementScopes.size != 1) Failure(TradeErrors.CHECKOUT_OFFER_INVALID)
            else if (profile == null || profile.status != UserProfileStatus.ACTIVE) {
                Failure(TradeErrors.CHECKOUT_BUYER_INVALID)
            } else
                when (
                    val address =
                        addresses.getByCode(
                            command.recipient.countryCode,
                            command.recipient.districtCode,
                        )
                ) {
                    is Failure -> address
                    is Success ->
                        Success(
                            PreparedCheckout(
                                command,
                                command.items.map { item ->
                                    val offer = byId.getValue(item.offerId)
                                    val goodsSnapshot = goodsBySpu.getValue(item.spuId)
                                    val sku =
                                        goodsSnapshot.skuSnapshots.single { it.skuId == item.skuId }
                                    com.jstore.trade.service.PreparedCheckoutItem(
                                        offer.offerId,
                                        offer.storeId,
                                        offer.merchantId,
                                        item.spuId,
                                        item.skuId,
                                        item.quantity,
                                        item.catalogSnapshotVersion,
                                        offer.offerVersion,
                                        offer.fulfillmentNodeId,
                                        offer.channelId,
                                        offer.price,
                                        goodsSnapshot.spuName,
                                        buildSkuDescription(sku.skuName, sku.attributes),
                                    )
                                },
                                TradeBuyerProfileSnapshot(profile.nickname, profile.phoneNumber),
                                address.value,
                                settlementScopes.single().third,
                            )
                        )
                }
        }

    private fun buildSkuDescription(
        skuName: String,
        attributes: List<Pair<String, String>>,
    ): String =
        if (attributes.isEmpty()) skuName
        else attributes.joinToString(" ") { "${it.first}:${it.second}" }
}
