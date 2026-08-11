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

import com.jstore.common.properties.Price
import com.jstore.order.domain.aftersale.AfterSaleId
import java.time.Instant

data class RefundableOrderItem(
    val orderItemId: OrderItemId,
    val purchasedQuantity: Int,
    val purchasedAmount: Price,
    val refundedQuantity: Int,
    val refundedAmount: Price,
    val refundableQuantity: Int,
    val refundableAmount: Price,
    val skuId: Long,
    val spuId: Long,
    val goodsName: String,
    val skuDescription: String,
)

data class RefundEligibility(
    val orderId: OrderId,
    val merchantId: MerchantId,
    val buyerId: Long,
    val paymentStatus: PaymentStatus,
    val tradeStatus: TradeStatus,
    val fulfillmentStatus: FulfillmentStatus,
    val paidAmount: Price,
    val totalRefundedAmount: Price,
    val currency: String,
    val items: List<RefundableOrderItem>,
)

data class SuccessfulRefundItem(val orderItemId: OrderItemId, val quantity: Int, val amount: Price)

data class RefundProjectionResult(val newlyRegistered: Boolean)

data class RefundFact(
    val refundId: String,
    val afterSaleId: AfterSaleId,
    val orderItemId: OrderItemId,
    val quantity: Int,
    val amount: Price,
    val occurredAt: Instant,
)
