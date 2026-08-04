package com.jstore.order.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.Page
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.getOrThrow
import com.jstore.common.utils.onFailure
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.SuccessfulRefundItem
import com.jstore.order.domain.order.command.OrderCancelCMD
import com.jstore.order.domain.order.command.OrderCreateCMD
import java.time.Instant

/** 订单应用服务 编排用例: 加载聚合 → 执行领域行为 → 保存 不包含业务规则，全部委托给领域对象 */
class OrderService(
    private val orderFactory: OrderFactory,
    private val orderRepository: OrderRepository,
    private val domainEventPublisher: DomainEventPublisher,
) {

    /** 根据ID查询订单 */
    fun getOrderById(orderId: OrderId): Result<Order, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        return Success(order)
    }

    /** 分页查询买家订单 */
    fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order> {
        return orderRepository.pageListByUserId(uid, currentPage, pageSize)
    }

    /** 创建订单 */
    fun createOrder(cmd: OrderCreateCMD): Result<Order, BusinessError> {
        cmd.validate().onFailure {
            return Failure(it)
        }
        val order = orderFactory.create(cmd).getOrThrow()
        orderRepository.add(order)
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(order)
    }

    /** 库存预扣成功回调 */
    fun confirmStock(orderId: OrderId): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.confirmStock().onFailure {
            return Failure(it)
        }
        orderRepository.save(order)
        return Success(Unit)
    }

    /** 库存不足，取消订单 */
    fun markStockInsufficient(orderId: OrderId, reason: String): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.markStockInsufficient(reason).onFailure {
            return Failure(it)
        }
        orderRepository.save(order)
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }

    /** 由支付集成事实驱动，不对 HTTP 控制器暴露。 */
    fun recordPaymentCaptured(
        orderId: OrderId,
        paymentReference: String,
        amount: Price,
        currency: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        val changed = order.recordPaymentCaptured(paymentReference, amount, currency, occurredAt)
        changed.onFailure {
            return Failure(it)
        }
        if (!changed.getOrThrow()) return Success(false)
        orderRepository.save(order)
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(true)
    }

    fun recordFulfillmentPrepared(
        orderId: OrderId,
        fulfillmentReference: String,
    ): Result<Boolean, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        val changed = order.recordFulfillmentPrepared(fulfillmentReference)
        changed.onFailure {
            return Failure(it)
        }
        if (!changed.getOrThrow()) return Success(false)
        orderRepository.save(order)
        return Success(true)
    }

    fun recordShipmentDispatched(
        orderId: OrderId,
        fulfillmentReference: String,
    ): Result<Boolean, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        val changed = order.recordShipmentDispatched(fulfillmentReference)
        changed.onFailure {
            return Failure(it)
        }
        if (!changed.getOrThrow()) return Success(false)
        orderRepository.save(order)
        return Success(true)
    }

    fun recordShipmentDelivered(
        orderId: OrderId,
        fulfillmentReference: String,
    ): Result<Boolean, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        val changed = order.recordShipmentDelivered(fulfillmentReference)
        changed.onFailure {
            return Failure(it)
        }
        if (!changed.getOrThrow()) return Success(false)
        orderRepository.save(order)
        return Success(true)
    }

    fun recordRefundSucceeded(
        orderId: OrderId,
        refundId: String,
        afterSaleId: AfterSaleId,
        items: List<SuccessfulRefundItem>,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        val projection = order.recordRefundSucceeded(refundId, afterSaleId, items, occurredAt)
        projection.onFailure {
            return Failure(it)
        }
        if (!projection.getOrThrow().newlyRegistered) return Success(false)
        orderRepository.save(order)
        return Success(true)
    }

    /** 完成订单 */
    fun completeOrder(orderId: OrderId): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.complete().onFailure {
            return Failure(it)
        }
        orderRepository.save(order)
        return Success(Unit)
    }

    /** 买家主动取消订单 */
    fun cancelOrder(cmd: OrderCancelCMD): Result<Unit, BusinessError> {
        cmd.validate().onFailure {
            return Failure(it)
        }
        val order =
            orderRepository.findById(cmd.orderId) ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.cancel(cmd.toReason()).onFailure {
            return Failure(it)
        }
        orderRepository.save(order)
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }
}
