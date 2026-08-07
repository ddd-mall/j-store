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

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId

data class AfterSaleItemRequestCMD(
    val orderItemId: OrderItemId,
    val quantity: Int,
    val amount: Price,
    val currency: String,
)

data class AfterSaleCreateCMD(
    val orderId: OrderId,
    val applicantId: ApplicantActorId,
    val reason: RefundReason,
    val items: List<AfterSaleItemRequestCMD>,
    val idempotencyKey: String,
) {
    fun validate(): Result<AfterSaleCreateCMD, BusinessError> {
        if (idempotencyKey.trim().length !in 1..128)
            return Failure(AfterSaleErrors.IDEMPOTENCY_KEY_INVALID)
        if (items.isEmpty() || items.size > 100) return Failure(AfterSaleErrors.ITEMS_EMPTY)
        if (items.map { it.orderItemId }.toSet().size != items.size)
            return Failure(AfterSaleErrors.ITEM_DUPLICATED)
        if (items.any { it.quantity <= 0 }) return Failure(AfterSaleErrors.QUANTITY_INVALID)
        if (items.any { it.amount <= Price.ZERO }) return Failure(AfterSaleErrors.AMOUNT_INVALID)
        if (items.any { it.currency != "CNY" }) return Failure(AfterSaleErrors.CURRENCY_MISMATCH)
        return Success(copy(idempotencyKey = idempotencyKey.trim()))
    }
}

data class AfterSaleApproveCMD(
    val afterSaleId: AfterSaleId,
    val merchantId: MerchantActorId,
    val idempotencyKey: String,
) {
    fun validate() = validateKey(this, idempotencyKey)
}

data class AfterSaleRejectCMD(
    val afterSaleId: AfterSaleId,
    val merchantId: MerchantActorId,
    val rejectionReason: String,
    val idempotencyKey: String,
) {
    fun validate(): Result<AfterSaleRejectCMD, BusinessError> =
        if (rejectionReason.trim().length !in 1..500)
            Failure(AfterSaleErrors.REJECTION_REASON_INVALID)
        else validateKey(this, idempotencyKey)
}

data class AfterSaleCancelCMD(
    val afterSaleId: AfterSaleId,
    val applicantId: ApplicantActorId,
    val idempotencyKey: String,
) {
    fun validate() = validateKey(this, idempotencyKey)
}

data class AfterSaleReceiveReturnCMD(val afterSaleId: AfterSaleId, val merchantId: MerchantActorId)

data class AfterSaleRetryRefundCMD(val afterSaleId: AfterSaleId, val merchantId: MerchantActorId)

private fun <T> validateKey(value: T, key: String): Result<T, BusinessError> =
    if (key.trim().length in 1..128) Success(value)
    else Failure(AfterSaleErrors.IDEMPOTENCY_KEY_INVALID)
