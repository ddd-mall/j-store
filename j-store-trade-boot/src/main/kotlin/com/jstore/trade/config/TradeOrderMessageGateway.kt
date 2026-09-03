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

import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.*
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.trade.domain.Trade
import com.jstore.trade.domain.TradeOrderPlan
import com.jstore.trade.service.TradeOrderCreationGateway
import java.security.MessageDigest
import java.time.Instant

class TradeOrderMessageGateway(private val publisher: IntegrationMessagePublisher) :
    TradeOrderCreationGateway {
    override fun requestOrderCreation(
        trade: Trade,
        plan: TradeOrderPlan,
        sourceMessageId: String,
        occurredAt: Instant,
    ): Result<Unit, com.jstore.common.errors.BusinessError> {
        publisher.publish(
            CreateOrderFromTradeIntegrationCommand(
                tradeId = trade.id.value,
                orderPlanId = plan.id.value,
                planDigest = trade.planDigest(plan),
                merchantId = plan.merchantId,
                buyer =
                    ContractAuthenticatedAccount(
                        trade.actingPrincipal.authenticationDomain,
                        trade.actingPrincipal.accountId,
                    ),
                buyerName = trade.buyerProfile.displayName,
                buyerPhone = trade.buyerProfile.phone,
                recipient =
                    ContractRecipient(
                        trade.recipient.name,
                        trade.recipient.phone,
                        trade.recipient.email,
                        trade.recipient.countryCode,
                        trade.recipient.districtCode,
                        trade.recipient.detailAddress,
                        trade.recipient.postalCode,
                        trade.recipient.customsFields,
                    ),
                shippingAddress =
                    ContractShippingAddress(
                        trade.recipient.shippingAddress.countryCode.value,
                        trade.recipient.shippingAddress.components.map {
                            ContractAddressComponent(
                                it.code,
                                it.level.depth,
                                it.level.name,
                                it.names.mapKeys { entry -> entry.key.toLanguageTag() },
                                it.defaultLocale.toLanguageTag(),
                            )
                        },
                    ),
                items =
                    plan.items.map {
                        ContractTradeOrderItem(
                            it.spuId,
                            it.skuId,
                            it.offerId,
                            it.storeId,
                            it.offerVersion,
                            it.fulfillmentNodeId,
                            it.channelId,
                            it.goodsName,
                            it.skuDescription,
                            it.quantity,
                            it.unitPrice.fen,
                            it.catalogSnapshotVersion,
                        )
                    },
                payableAmountFen = plan.payableAmount.fen,
                currency = trade.currency,
                sourceMessageId = sourceMessageId,
                occurredAtValue = occurredAt,
            )
        )
        return Success(Unit)
    }

    override fun requestOrderCancellation(
        trade: Trade,
        plan: TradeOrderPlan,
        reason: String,
        sourceMessageId: String,
        occurredAt: Instant,
    ): Result<Unit, com.jstore.common.errors.BusinessError> {
        publisher.publish(
            CancelOrderFromTradeIntegrationCommand(
                trade.id.value,
                plan.id.value,
                reason,
                sourceMessageId,
                occurredAt,
            )
        )
        return Success(Unit)
    }

    private fun Trade.planDigest(plan: TradeOrderPlan): String {
        val canonical = buildString {
            append("v1|").append(id.value).append('|').append(plan.id.value).append('|')
            append(requestDigest).append('|').append(plan.merchantId).append('|')
            append(buyerProfile.displayName).append('|').append(buyerProfile.phone.orEmpty())
            append('|').append(recipient.shippingAddress.getLeafCode()).append('|')
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
