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
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.command.AfterSaleCreateCMD
import com.jstore.order.domain.order.Order
import java.time.Instant
import java.time.LocalDateTime

interface AfterSaleFactory {
    fun create(
        cmd: AfterSaleCreateCMD,
        order: Order,
        merchantId: MerchantActorId,
        now: LocalDateTime,
        occurredAt: Instant,
    ): Result<AfterSale, BusinessError>
}

class AfterSaleFactoryImpl(private val sequence: SnowFlakSequence) : AfterSaleFactory {
    override fun create(
        cmd: AfterSaleCreateCMD,
        order: Order,
        merchantId: MerchantActorId,
        now: LocalDateTime,
        occurredAt: Instant,
    ): Result<AfterSale, BusinessError> {
        val eligibility =
            when (val result = order.refundEligibility()) {
                is Success -> result.value
                is Failure -> return Failure(AfterSaleErrors.ORDER_NOT_ELIGIBLE)
            }
        if (eligibility.orderId != cmd.orderId || eligibility.buyerId != cmd.applicantId.value)
            return Failure(AfterSaleErrors.APPLICANT_FORBIDDEN)
        val eligibleById = eligibility.items.associateBy { it.orderItemId }
        val items =
            cmd.items.map { request ->
                val eligible =
                    eligibleById[request.orderItemId]
                        ?: return Failure(AfterSaleErrors.ITEM_NOT_FOUND)
                if (
                    request.quantity > eligible.refundableQuantity ||
                        request.amount > eligible.refundableAmount
                )
                    return Failure(AfterSaleErrors.NO_REFUND_CAPACITY)
                val snapshot =
                    RefundEligibilitySnapshot(
                        eligible.orderItemId,
                        eligible.refundableQuantity,
                        eligible.refundableAmount,
                        eligibility.currency,
                        GoodsSnapshot(
                            eligible.skuId,
                            eligible.spuId,
                            eligible.goodsName,
                            eligible.skuDescription,
                        ),
                    )
                AfterSaleItemImpl(
                    AfterSaleItemId(sequence.nextId()),
                    cmd.orderId,
                    request.orderItemId,
                    request.quantity,
                    request.amount,
                    eligibility.currency,
                    snapshot,
                )
            }
        val requireReturn =
            eligibility.fulfillmentStatus in
                setOf(
                    com.jstore.order.domain.order.FulfillmentStatus.SHIPPED,
                    com.jstore.order.domain.order.FulfillmentStatus.DELIVERED,
                )
        val afterSale =
            AfterSaleImpl(
                AfterSaleId(sequence.nextId()),
                cmd.orderId,
                cmd.applicantId,
                merchantId,
                AfterSaleStatus.REQUESTED,
                cmd.reason,
                FulfillmentSnapshot(eligibility.fulfillmentStatus, requireReturn),
                items,
                createTime = now,
                _updateTime = now,
            )
        afterSale.recordRequested(occurredAt)
        return Success(afterSale)
    }
}
