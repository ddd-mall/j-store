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
package com.jstore.order.config

import com.jstore.common.utils.map
import com.jstore.order.service.CreateOrderFromTradeCommand
import com.jstore.order.service.CreateOrderFromTradeItem
import com.jstore.order.service.InternalOrderCreationUseCase
import com.jstore.trade.domain.PartyType
import com.jstore.trade.domain.Trade
import com.jstore.trade.domain.TradeOrderPlan
import com.jstore.trade.service.TradeOrderCreationGateway
import java.security.MessageDigest

/** Trusted adapter invoked by Trade only after sale authorization and inventory reservation. */
class TradeOrderCreationAdapter(private val orders: InternalOrderCreationUseCase) :
    TradeOrderCreationGateway {
    override fun createOrder(trade: Trade, plan: TradeOrderPlan) =
        orders.createOrder(trade.toOrderCommand(plan)).map { it.id.value }

    override fun cancelOrder(plan: TradeOrderPlan, reason: String) =
        orders.cancelOrder(plan.id.value, reason)

    private fun Trade.toOrderCommand(plan: TradeOrderPlan): CreateOrderFromTradeCommand {
        require(buyerParty.partyType == PartyType.INDIVIDUAL) {
            "The current Order model only accepts an individual buyer"
        }
        return CreateOrderFromTradeCommand(
            tradeId = id.value,
            orderPlanId = plan.id.value,
            planDigest = planDigest(plan),
            merchantId = plan.merchantId,
            buyerId = buyerParty.partyId,
            buyerName = buyerProfile.displayName,
            buyerPhone = buyerProfile.phone,
            recipientName = recipient.name,
            recipientPhone = recipient.phone,
            recipientEmail = recipient.email,
            shippingAddress = recipient.shippingAddress,
            detailAddress = recipient.detailAddress,
            postalCode = recipient.postalCode,
            customsFields = recipient.customsFields,
            items =
                plan.items.map {
                    CreateOrderFromTradeItem(
                        spuId = it.spuId,
                        skuId = it.skuId,
                        offerId = it.offerId,
                        storeId = it.storeId,
                        offerVersion = it.offerVersion,
                        fulfillmentNodeId = it.fulfillmentNodeId,
                        channelId = it.channelId,
                        goodsName = it.goodsName,
                        skuDescription = it.skuDescription,
                        quantity = it.quantity,
                        unitPrice = it.unitPrice,
                        catalogSnapshotVersion = it.catalogSnapshotVersion,
                    )
                },
            payableAmount = plan.payableAmount,
            currency = currency,
        )
    }

    private fun Trade.planDigest(plan: TradeOrderPlan): String {
        val canonical = buildString {
            append("v1|").append(id.value).append('|').append(plan.id.value).append('|')
            append(requestDigest).append('|').append(plan.merchantId).append('|')
            append(buyerProfile.displayName)
                .append('|')
                .append(buyerProfile.phone.orEmpty())
                .append('|')
            append(recipient.shippingAddress.getLeafCode()).append('|')
            append(recipient.detailAddress).append('|').append(plan.payableAmount.fen).append('|')
            plan.items
                .sortedBy { it.offerId }
                .forEach {
                    append(it.offerId).append(':').append(it.skuId).append(':')
                    append(it.quantity).append(':').append(it.unitPrice.fen).append(':')
                    append(it.goodsName).append(':').append(it.skuDescription).append(';')
                }
        }
        return "v1:" +
            MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") {
                "%02x".format(it)
            }
    }
}
