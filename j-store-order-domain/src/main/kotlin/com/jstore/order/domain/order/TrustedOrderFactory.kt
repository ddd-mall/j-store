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
package com.jstore.order.domain.order

import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success

data class TrustedOrderItemDraft(
    val spuId: Long,
    val skuId: Long,
    val offerId: Long,
    val storeId: Long,
    val offerVersion: Long,
    val fulfillmentNodeId: String,
    val channelId: String,
    val goodsName: String,
    val skuDescription: String,
    val quantity: Int,
    val unitPrice: Price,
    val catalogSnapshotVersion: Long,
)

data class TrustedOrderDraft(
    val tradeId: Long,
    val orderPlanId: Long,
    val planDigest: String,
    val merchantId: Long,
    val buyerAuthenticationDomain: String,
    val buyerId: Long,
    val buyerName: String,
    val buyerPhone: String?,
    val recipientName: String,
    val recipientPhone: String?,
    val recipientEmail: String?,
    val shippingAddress: I18nGeoAddress,
    val detailAddress: String,
    val postalCode: String?,
    val customsFields: Map<String, String>,
    val items: List<TrustedOrderItemDraft>,
    val payableAmount: Price,
    val currency: String,
)

fun interface TrustedOrderFactory {
    fun create(draft: TrustedOrderDraft): Result<Order, com.jstore.common.errors.BusinessError>
}

class TrustedOrderFactoryImpl(private val sequence: SnowFlakSequence) : TrustedOrderFactory {
    override fun create(
        draft: TrustedOrderDraft
    ): Result<Order, com.jstore.common.errors.BusinessError> {
        if (
            draft.tradeId <= 0 ||
                draft.orderPlanId <= 0 ||
                draft.planDigest.isBlank() ||
                draft.merchantId <= 0 ||
                draft.buyerAuthenticationDomain.isBlank() ||
                draft.buyerId <= 0 ||
                draft.buyerName.isBlank() ||
                draft.items.isEmpty() ||
                draft.currency.isBlank()
        )
            return Failure(OrderErrors.TRADE_PLAN_CONFLICT)

        val items =
            draft.items.map {
                OrderItemImpl(
                    id = OrderItemId(sequence.nextId()),
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
                    snapshotVersion = it.catalogSnapshotVersion,
                )
            }
        val subtotal = Price.sumOf(items.map { it.purchasedAmount })
        if (subtotal != draft.payableAmount) return Failure(OrderErrors.TRADE_PLAN_CONFLICT)

        val order =
            OrderImpl(
                id = OrderId(sequence.nextId()),
                merchantId = MerchantId(draft.merchantId),
                buyerInfo =
                    UserInfo(
                        draft.buyerAuthenticationDomain,
                        draft.buyerId,
                        draft.buyerPhone?.let(::PhoneNumber),
                        draft.buyerName,
                    ),
                _items = items.toMutableList(),
                recipientInfo =
                    RecipientInfo(
                        draft.recipientName,
                        ContractInfo(
                            draft.recipientEmail,
                            draft.recipientPhone?.let(::PhoneNumber),
                        ),
                        draft.shippingAddress,
                        draft.detailAddress,
                        draft.postalCode,
                        draft.customsFields,
                    ),
                _tradeStatus = TradeStatus.ACTIVE,
                _paymentStatus = PaymentStatus.UNPAID,
                _fulfillmentStatus = FulfillmentStatus.UNFULFILLED,
                _commitmentStatus = CommitmentStatus.CONFIRMED,
                amountSnapshot =
                    OrderAmountSnapshot(
                        draft.currency,
                        subtotal,
                        Price.ZERO,
                        Price.ZERO,
                        Price.ZERO,
                        draft.payableAmount,
                    ),
                sourceTradeId = draft.tradeId,
                sourceOrderPlanId = draft.orderPlanId,
                sourcePlanDigest = draft.planDigest,
            )
        order.recordCreated()
        return Success(order)
    }
}
