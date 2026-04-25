package com.jstore.order.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.getOrThrow
import com.jstore.common.utils.onFailure
import com.jstore.order.domain.order.CancellationReason
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.RefundReason
import com.jstore.order.domain.order.command.OrderCreateCMD

/**
 * 订单应用服务
 * 编排用例: 加载聚合 → 执行领域行为 → 保存
 * 不包含业务规则，全部委托给领域对象
 */
class OrderService(
    private val orderFactory: OrderFactory,
    private val orderRepository: OrderRepository,
    private val domainEventPublisher: DomainEventPublisher,
) {

    /** 创建订单 */
    fun createOrder(cmd: OrderCreateCMD): Result<Order, BusinessError> {
        cmd.validate().onFailure { return Failure(it) }
        val order = orderFactory.create(cmd).getOrThrow()
        orderRepository.add(order)
        // 发布聚合根上积累的领域事件（OrderCreatedEvent）
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(order)
    }

    /** 库存预扣成功回调 */
    fun confirmStock(orderId: OrderId): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.confirmStock().onFailure { return Failure(it) }
        orderRepository.save(order)
        return Success(Unit)
    }

    /** 库存不足，取消订单 */
    fun markStockInsufficient(orderId: OrderId, reason: String): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.markStockInsufficient(reason).onFailure { return Failure(it) }
        orderRepository.save(order)
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }

    /** 支付回调 */
    fun payOrder(orderId: OrderId, paidAmount: Price): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.pay(paidAmount).onFailure { return Failure(it) }
        orderRepository.save(order)
        // 发布 OrderPaidEvent，触发库存 confirm（真正扣减）
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }

    /** 确认备货（支付后 → 待发货） */
    fun confirmForShipment(orderId: OrderId): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.confirmForShipment().onFailure { return Failure(it) }
        orderRepository.save(order)
        return Success(Unit)
    }

    /** 发货 */
    fun shipOrder(orderId: OrderId): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.ship().onFailure { return Failure(it) }
        orderRepository.save(order)
        return Success(Unit)
    }

    /** 确认收货 */
    fun confirmDelivery(orderId: OrderId): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.confirmDelivery().onFailure { return Failure(it) }
        orderRepository.save(order)
        return Success(Unit)
    }

    /** 完成订单 */
    fun completeOrder(orderId: OrderId): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.complete().onFailure { return Failure(it) }
        orderRepository.save(order)
        return Success(Unit)
    }

    /** 买家主动取消订单 */
    fun cancelOrder(orderId: OrderId, reason: CancellationReason): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.cancel(reason).onFailure { return Failure(it) }
        orderRepository.save(order)
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }

    /** 申请退款（已支付未发货 / 已签收退货退款） */
    fun requestRefund(orderId: OrderId, reason: RefundReason, itemIds: List<OrderItemId>): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.requestRefund(reason, itemIds).onFailure { return Failure(it) }
        orderRepository.save(order)
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }

    /** 卖家批准退款 */
    fun approveRefund(orderId: OrderId, itemIds: List<OrderItemId>): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.approveRefund(itemIds).onFailure { return Failure(it) }
        orderRepository.save(order)
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }

    /** 卖家拒绝退款 */
    fun rejectRefund(orderId: OrderId, rejectReason: String, itemIds: List<OrderItemId>): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.rejectRefund(rejectReason, itemIds).onFailure { return Failure(it) }
        orderRepository.save(order)
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }
}
