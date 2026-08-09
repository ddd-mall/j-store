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
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.properties.Price
import com.jstore.common.query.Page
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.map
import com.jstore.common.utils.onFailure
import com.jstore.common.utils.onSuccess
import com.jstore.order.acl.UserService
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.SaleAuthorizationRef
import com.jstore.order.domain.order.SuccessfulRefundItem
import com.jstore.order.domain.order.command.OrderCancelCMD
import com.jstore.order.domain.order.command.OrderCreateCMD
import java.time.Instant

/** 订单应用服务 编排用例: 加载聚合 → 执行领域行为 → 保存 不包含业务规则，全部委托给领域对象 */
class OrderService(
    private val orderFactory: OrderFactory,
    private val orderRepository: OrderRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val userService: UserService,
) : OrderUseCase {

    /** 根据ID查询订单 */
    override fun getOrderById(buyerId: Long, orderId: OrderId): Result<Order, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        if (order.buyerInfo.uid != buyerId) return Failure(OrderErrors.ORDER_NOT_FOUND)
        return Success(order)
    }

    /** 分页查询买家订单 */
    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order> {
        return orderRepository.pageListByUserId(uid, currentPage, pageSize)
    }

    /** 创建订单 */
    override fun createOrder(cmd: OrderCreateCMD): Result<Order, BusinessError> {
        cmd.validate().onFailure {
            return Failure(it)
        }
        val buyerInfo =
            userService.findUserInfo(cmd.buyerUid) ?: return Failure(OrderErrors.BUYER_INVALID)
        return orderFactory.create(cmd, buyerInfo).onSuccess { order ->
            orderRepository.add(order)
            order.publishPendingEvents(domainEventPublisher)
        }
    }

    override fun recordSaleAuthorized(
        orderId: OrderId,
        authorizations: List<SaleAuthorizationRef>,
    ): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.recordSaleAuthorized(authorizations).onFailure {
            return Failure(it)
        }
        orderRepository.save(order)
        order.publishPendingEvents(domainEventPublisher)
        return Success(Unit)
    }

    override fun markSaleAuthorizationFailed(
        orderId: OrderId,
        reason: String,
    ): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.markSaleAuthorizationFailed(reason).onFailure {
            return Failure(it)
        }
        orderRepository.save(order)
        order.publishPendingEvents(domainEventPublisher)
        return Success(Unit)
    }

    /** 库存预扣成功回调 */
    override fun confirmStock(orderId: OrderId): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.confirmStock().onFailure {
            return Failure(it)
        }
        orderRepository.save(order)
        order.publishPendingEvents(domainEventPublisher)
        return Success(Unit)
    }

    /** 库存不足，取消订单 */
    override fun markStockInsufficient(
        orderId: OrderId,
        reason: String,
    ): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.markStockInsufficient(reason).onFailure {
            return Failure(it)
        }
        orderRepository.save(order)
        order.publishPendingEvents(domainEventPublisher)
        return Success(Unit)
    }

    /** 由支付集成事实驱动，不对 HTTP 控制器暴露。 */
    override fun recordPaymentCaptured(
        orderId: OrderId,
        paymentReference: String,
        amount: Price,
        currency: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        val changed = order.recordPaymentCaptured(paymentReference, amount, currency, occurredAt)
        return changed.onSuccess { didChange ->
            if (didChange) {
                orderRepository.save(order)
                order.publishPendingEvents(domainEventPublisher)
            }
        }
    }

    override fun recordFulfillmentPrepared(
        orderId: OrderId,
        fulfillmentReference: String,
    ): Result<Boolean, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        val changed = order.recordFulfillmentPrepared(fulfillmentReference)
        return changed.onSuccess { didChange ->
            if (didChange) {
                orderRepository.save(order)
                order.publishPendingEvents(domainEventPublisher)
            }
        }
    }

    override fun recordShipmentDispatched(
        orderId: OrderId,
        fulfillmentReference: String,
    ): Result<Boolean, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        val changed = order.recordShipmentDispatched(fulfillmentReference)
        return changed.onSuccess { didChange ->
            if (didChange) {
                orderRepository.save(order)
                order.publishPendingEvents(domainEventPublisher)
            }
        }
    }

    override fun recordShipmentDelivered(
        orderId: OrderId,
        fulfillmentReference: String,
    ): Result<Boolean, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        val changed = order.recordShipmentDelivered(fulfillmentReference)
        return changed.onSuccess { didChange ->
            if (didChange) {
                orderRepository.save(order)
                order.publishPendingEvents(domainEventPublisher)
            }
        }
    }

    override fun recordRefundSucceeded(
        orderId: OrderId,
        refundId: String,
        afterSaleId: AfterSaleId,
        items: List<SuccessfulRefundItem>,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        val projection = order.recordRefundSucceeded(refundId, afterSaleId, items, occurredAt)
        return projection
            .onSuccess { result ->
                if (result.newlyRegistered) {
                    orderRepository.save(order)
                    order.publishPendingEvents(domainEventPublisher)
                }
            }
            .map { it.newlyRegistered }
    }

    /** 完成订单 */
    override fun completeOrder(orderId: OrderId): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.complete().onFailure {
            return Failure(it)
        }
        orderRepository.save(order)
        order.publishPendingEvents(domainEventPublisher)
        return Success(Unit)
    }

    /** 买家主动取消订单 */
    override fun cancelOrder(buyerId: Long, cmd: OrderCancelCMD): Result<Unit, BusinessError> {
        cmd.validate().onFailure {
            return Failure(it)
        }
        val order =
            orderRepository.findById(cmd.orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        if (order.buyerInfo.uid != buyerId) return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.cancel(cmd.toReason()).onFailure {
            return Failure(it)
        }
        orderRepository.save(order)
        order.publishPendingEvents(domainEventPublisher)
        return Success(Unit)
    }
}
