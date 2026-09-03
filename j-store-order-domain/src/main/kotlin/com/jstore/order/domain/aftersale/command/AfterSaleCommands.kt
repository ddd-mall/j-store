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
package com.jstore.order.domain.aftersale.command

import com.jstore.common.properties.Price
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId

data class AfterSaleItemRequestCMD(
    val orderItemId: OrderItemId,
    val quantity: Int,
    val amount: Price,
)

data class AfterSaleCreateCMD(
    val orderId: OrderId,
    val applicantId: ApplicantActorId,
    val reason: RefundReason,
    val items: List<AfterSaleItemRequestCMD>,
    val idempotencyKey: String,
)

data class AfterSaleApproveCMD(
    val afterSaleId: AfterSaleId,
    val merchantId: MerchantActorId,
    val idempotencyKey: String,
)

data class AfterSaleRejectCMD(
    val afterSaleId: AfterSaleId,
    val merchantId: MerchantActorId,
    val rejectionReason: String,
    val idempotencyKey: String,
)

data class AfterSaleCancelCMD(
    val afterSaleId: AfterSaleId,
    val applicantId: ApplicantActorId,
    val idempotencyKey: String,
)

data class AfterSaleReceiveReturnCMD(val afterSaleId: AfterSaleId, val merchantId: MerchantActorId)

data class AfterSaleRetryRefundCMD(val afterSaleId: AfterSaleId, val merchantId: MerchantActorId)
