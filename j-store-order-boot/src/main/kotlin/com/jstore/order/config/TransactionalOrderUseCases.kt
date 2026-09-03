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
import com.jstore.order.service.AfterSaleOrderAccess
import com.jstore.order.service.AfterSaleUseCase
import com.jstore.order.service.CreateOrderFromTradeCommand
import com.jstore.order.service.InternalOrderCreationUseCase
import com.jstore.order.service.OrderUseCase
import java.time.Instant
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** Spring transaction boundary around the framework-free application service. */
class TransactionalOrderUseCase(
    private val delegate: OrderUseCase,
    transactionManager: PlatformTransactionManager,
) : OrderUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun getOrderById(
        buyerAuthenticationDomain: String,
        buyerId: Long,
        orderId: OrderId,
    ) = read {
        delegate.getOrderById(buyerAuthenticationDomain, buyerId, orderId)
    }

    override fun pageListByUserId(
        buyerAuthenticationDomain: String,
        uid: Long,
        currentPage: Int,
        pageSize: Int,
    ): Page<Order> = read {
        delegate.pageListByUserId(buyerAuthenticationDomain, uid, currentPage, pageSize)
    }

    override fun confirmTradeCommitment(orderId: OrderId) = write {
        delegate.confirmTradeCommitment(orderId)
    }

    override fun rejectTradeCommitment(orderId: OrderId, reason: String) = write {
        delegate.rejectTradeCommitment(orderId, reason)
    }

    override fun recordPaymentCaptured(
        orderId: OrderId,
        paymentReference: String,
        amount: Price,
        currency: String,
        occurredAt: Instant,
    ) = write {
        delegate.recordPaymentCaptured(orderId, paymentReference, amount, currency, occurredAt)
    }

    override fun recordFulfillmentPrepared(orderId: OrderId, fulfillmentReference: String) = write {
        delegate.recordFulfillmentPrepared(orderId, fulfillmentReference)
    }

    override fun recordShipmentDispatched(orderId: OrderId, fulfillmentReference: String) = write {
        delegate.recordShipmentDispatched(orderId, fulfillmentReference)
    }

    override fun recordShipmentDelivered(orderId: OrderId, fulfillmentReference: String) = write {
        delegate.recordShipmentDelivered(orderId, fulfillmentReference)
    }

    override fun recordRefundSucceeded(
        orderId: OrderId,
        refundId: String,
        afterSaleId: AfterSaleId,
        items: List<SuccessfulRefundItem>,
        occurredAt: Instant,
    ) = write { delegate.recordRefundSucceeded(orderId, refundId, afterSaleId, items, occurredAt) }

    override fun completeOrder(orderId: OrderId) = write { delegate.completeOrder(orderId) }

    override fun cancelOrder(
        buyerAuthenticationDomain: String,
        buyerId: Long,
        cmd: OrderCancelCMD,
    ) = write {
        delegate.cancelOrder(buyerAuthenticationDomain, buyerId, cmd)
    }

    private fun <T> read(block: () -> T): T = requireNotNull(read.execute { block() })

    private fun <T> write(block: () -> T): T = requireNotNull(write.execute { block() })
}

class TransactionalInternalOrderCreationUseCase(
    private val delegate: InternalOrderCreationUseCase,
    transactionManager: PlatformTransactionManager,
) : InternalOrderCreationUseCase {
    private val write = TransactionTemplate(transactionManager)

    override fun createOrder(cmd: CreateOrderFromTradeCommand) =
        requireNotNull(write.execute { delegate.createOrder(cmd) })

    override fun cancelOrder(tradeId: Long, orderPlanId: Long, reason: String) =
        requireNotNull(write.execute { delegate.cancelOrder(tradeId, orderPlanId, reason) })
}

/** Spring transaction boundary for after-sale commands and consistent reads. */
class TransactionalAfterSaleUseCase(
    private val delegate: AfterSaleUseCase,
    transactionManager: PlatformTransactionManager,
) : AfterSaleUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun findById(id: AfterSaleId): Result<AfterSale, BusinessError> = read {
        delegate.findById(id)
    }

    override fun listByOrderForAccess(
        orderId: OrderId
    ): Result<AfterSaleOrderAccess, BusinessError> = read { delegate.listByOrderForAccess(orderId) }

    override fun create(buyerAuthenticationDomain: String, cmd: AfterSaleCreateCMD) = write {
        delegate.create(buyerAuthenticationDomain, cmd)
    }

    override fun approve(cmd: AfterSaleApproveCMD) = write { delegate.approve(cmd) }

    override fun reject(cmd: AfterSaleRejectCMD) = write { delegate.reject(cmd) }

    override fun cancel(buyerAuthenticationDomain: String, cmd: AfterSaleCancelCMD) = write {
        delegate.cancel(buyerAuthenticationDomain, cmd)
    }

    override fun receiveReturn(cmd: AfterSaleReceiveReturnCMD) = write {
        delegate.receiveReturn(cmd)
    }

    override fun retryRefund(cmd: AfterSaleRetryRefundCMD) = write { delegate.retryRefund(cmd) }

    override fun recordRefundSucceeded(
        afterSaleId: AfterSaleId,
        refundId: String,
        occurredAt: Instant,
    ) = write { delegate.recordRefundSucceeded(afterSaleId, refundId, occurredAt) }

    override fun recordRefundFailed(
        afterSaleId: AfterSaleId,
        refundId: String,
        reason: String,
        occurredAt: Instant,
    ) = write { delegate.recordRefundFailed(afterSaleId, refundId, reason, occurredAt) }

    private fun <T> read(block: () -> T): T = requireNotNull(read.execute { block() })

    private fun <T> write(block: () -> T): T = requireNotNull(write.execute { block() })
}
