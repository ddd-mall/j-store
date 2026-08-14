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
package com.jstore.order.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Price
import com.jstore.common.query.Page
import com.jstore.common.utils.Result
import com.jstore.order.domain.aftersale.AfterSale
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.aftersale.command.AfterSaleApproveCMD
import com.jstore.order.domain.aftersale.command.AfterSaleCancelCMD
import com.jstore.order.domain.aftersale.command.AfterSaleCreateCMD
import com.jstore.order.domain.aftersale.command.AfterSaleReceiveReturnCMD
import com.jstore.order.domain.aftersale.command.AfterSaleRejectCMD
import com.jstore.order.domain.aftersale.command.AfterSaleRetryRefundCMD
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.SuccessfulRefundItem
import com.jstore.order.domain.order.command.OrderCancelCMD
import com.jstore.order.domain.order.command.OrderCreateCMD
import java.time.Instant

/**
 * Stable inbound port for order use cases. Framework transaction concerns belong to boot adapters.
 */
interface OrderUseCase {
    fun getOrderById(buyerId: Long, orderId: OrderId): Result<Order, BusinessError>

    fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order>

    fun createOrder(cmd: OrderCreateCMD): Result<Order, BusinessError>

    fun confirmTradeCommitment(orderId: OrderId): Result<Unit, BusinessError>

    fun rejectTradeCommitment(orderId: OrderId, reason: String): Result<Unit, BusinessError>

    fun recordPaymentCaptured(
        orderId: OrderId,
        paymentReference: String,
        amount: Price,
        currency: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    fun recordFulfillmentPrepared(
        orderId: OrderId,
        fulfillmentReference: String,
    ): Result<Boolean, BusinessError>

    fun recordShipmentDispatched(
        orderId: OrderId,
        fulfillmentReference: String,
    ): Result<Boolean, BusinessError>

    fun recordShipmentDelivered(
        orderId: OrderId,
        fulfillmentReference: String,
    ): Result<Boolean, BusinessError>

    fun recordRefundSucceeded(
        orderId: OrderId,
        refundId: String,
        afterSaleId: AfterSaleId,
        items: List<SuccessfulRefundItem>,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    fun completeOrder(orderId: OrderId): Result<Unit, BusinessError>

    fun cancelOrder(buyerId: Long, cmd: OrderCancelCMD): Result<Unit, BusinessError>
}

interface AfterSaleUseCase {
    fun findById(id: AfterSaleId): Result<AfterSale, BusinessError>

    fun listByOrderForAccess(orderId: OrderId): Result<AfterSaleOrderAccess, BusinessError>

    fun create(cmd: AfterSaleCreateCMD): Result<AfterSale, BusinessError>

    fun approve(cmd: AfterSaleApproveCMD): Result<AfterSale, BusinessError>

    fun reject(cmd: AfterSaleRejectCMD): Result<AfterSale, BusinessError>

    fun cancel(cmd: AfterSaleCancelCMD): Result<AfterSale, BusinessError>

    fun receiveReturn(cmd: AfterSaleReceiveReturnCMD): Result<AfterSale, BusinessError>

    fun retryRefund(cmd: AfterSaleRetryRefundCMD): Result<AfterSale, BusinessError>

    fun recordRefundSucceeded(
        afterSaleId: AfterSaleId,
        refundId: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    fun recordRefundFailed(
        afterSaleId: AfterSaleId,
        refundId: String,
        reason: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>
}
