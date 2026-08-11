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
package com.jstore.order.domain.aftersale

import com.jstore.common.errors.BusinessError

object AfterSaleErrors {
    val NOT_FOUND = BusinessError("售后单不存在", "AfterSale.NotFound", 404)
    val ORDER_NOT_FOUND = BusinessError("订单不存在", "AfterSale.Order.NotFound", 404)
    val ITEMS_EMPTY = BusinessError("售后行项不能为空", "AfterSale.Items.Empty", 400)
    val ITEM_DUPLICATED = BusinessError("售后行项重复", "AfterSale.Items.Duplicated", 400)
    val ITEM_NOT_FOUND = BusinessError("订单行项不存在", "AfterSale.Items.NotFound", 400)
    val QUANTITY_INVALID = BusinessError("退款数量无效", "AfterSale.Request.QuantityInvalid", 400)
    val AMOUNT_INVALID = BusinessError("退款金额无效", "AfterSale.Request.AmountInvalid", 400)
    val CURRENCY_MISMATCH = BusinessError("退款币种不一致", "AfterSale.Request.CurrencyMismatch", 400)
    val ORDER_NOT_ELIGIBLE = BusinessError("订单不允许售后", "AfterSale.Order.NotEligible", 409)
    val NO_REFUND_CAPACITY = BusinessError("订单无可退款容量", "AfterSale.Order.NoRefundCapacity", 409)
    val CAPACITY_EXCEEDED = BusinessError("售后容量已超限", "AfterSale.Capacity.Exceeded", 409)
    val ILLEGAL_STATE = BusinessError("售后状态不合法", "AfterSale.State.Invalid", 409)
    val APPLICANT_FORBIDDEN = BusinessError("申请人无权操作", "AfterSale.Actor.ApplicantForbidden", 403)
    val MERCHANT_FORBIDDEN = BusinessError("商家无权操作", "AfterSale.Actor.MerchantForbidden", 403)
    val REASON_INVALID = BusinessError("售后原因无效", "AfterSale.Reason.Invalid", 400)
    val REJECTION_REASON_INVALID = BusinessError("拒绝原因无效", "AfterSale.Reason.RejectionInvalid", 400)
    val IDEMPOTENCY_KEY_INVALID = BusinessError("幂等键无效", "AfterSale.IdempotencyKey.Invalid", 400)
    val IDEMPOTENCY_CONFLICT = BusinessError("幂等命令冲突", "AfterSale.Idempotency.Conflict", 409)
    val CONCURRENT_MODIFICATION =
        BusinessError("售后单已被并发修改", "AfterSale.ConcurrentModification", 409)
    val REFUND_REFERENCE_CONFLICT =
        BusinessError("售后单已关联其他退款流水", "AfterSale.Refund.ReferenceConflict", 409)
}
