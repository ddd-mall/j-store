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
import com.jstore.common.framework.AgreeGate
import com.jstore.common.utils.Result
import com.jstore.order.domain.order.OrderId
import java.time.Instant
import java.time.LocalDateTime

interface AfterSale : AgreeGate<AfterSaleId> {
    override val id: AfterSaleId
    val orderId: OrderId
    val applicantId: ApplicantActorId
    val merchantId: MerchantActorId
    val status: AfterSaleStatus
    val reason: RefundReason
    val fulfillmentSnapshot: FulfillmentSnapshot
    val items: List<AfterSaleItem>
    val reviewDecision: ReviewDecision?
    val cancelledAt: LocalDateTime?
    val returnReceivedAt: LocalDateTime?
    val refundId: String?
    val refundFailureReason: String?
    val createTime: LocalDateTime
    val updateTime: LocalDateTime
    val version: Long

    fun approve(reviewerId: MerchantActorId, occurredAt: Instant): Result<Unit, BusinessError>

    fun reject(
        reviewerId: MerchantActorId,
        reason: String,
        occurredAt: Instant,
    ): Result<Unit, BusinessError>

    fun cancel(applicantId: ApplicantActorId, occurredAt: Instant): Result<Unit, BusinessError>

    fun receiveReturn(
        reviewerId: MerchantActorId,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    fun retryRefund(
        reviewerId: MerchantActorId,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    fun markRefundSucceeded(refundId: String, occurredAt: Instant): Result<Boolean, BusinessError>

    fun markRefundFailed(
        refundId: String,
        reason: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>
}
