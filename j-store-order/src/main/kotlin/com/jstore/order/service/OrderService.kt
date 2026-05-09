package com.jstore.order.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.getOrThrow
import com.jstore.common.utils.onFailure
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.command.OrderApproveRefundCMD
import com.jstore.order.domain.order.command.OrderCancelCMD
import com.jstore.order.domain.order.command.OrderCreateCMD
import com.jstore.order.domain.order.command.OrderPayCMD
import com.jstore.order.domain.order.command.OrderRejectRefundCMD
import com.jstore.order.domain.order.command.OrderRequestRefundCMD
import com.jstore.common.framework.Page

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

    /** 根据ID查询订单 */
    fun getOrderById(orderId: OrderId): Result<Order, BusinessError> {
        val order = orderRepository.findById(orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        return Success(order)
    }

    /** 分页查询买家订单 */
    fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order> {
        return orderRepository.pageListByUserId(uid, currentPage, pageSize)
    }

    /** 创建订单 */
    fun createOrder(cmd: OrderCreateCMD): Result<Order, BusinessError> {
        cmd.validate().onFailure { return Failure(it) }
        val order = orderFactory.create(cmd).getOrThrow()
        orderRepository.add(order)
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
    fun payOrder(cmd: OrderPayCMD): Result<Unit, BusinessError> {
        cmd.validate().onFailure { return Failure(it) }
        val order = orderRepository.findById(cmd.orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.pay(cmd.paidAmount).onFailure { return Failure(it) }
        orderRepository.save(order)
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
    fun cancelOrder(cmd: OrderCancelCMD): Result<Unit, BusinessError> {
        cmd.validate().onFailure { return Failure(it) }
        val order = orderRepository.findById(cmd.orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.cancel(cmd.toReason()).onFailure { return Failure(it) }
        orderRepository.save(order)
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }

    /** 申请退款（已支付未发货 / 已签收退货退款） */
    fun requestRefund(cmd: OrderRequestRefundCMD): Result<Unit, BusinessError> {
        cmd.validate().onFailure { return Failure(it) }
        val order = orderRepository.findById(cmd.orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.requestRefund(cmd.toReason(), cmd.itemIds).onFailure { return Failure(it) }
        orderRepository.save(order)
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }

    /** 卖家批准退款 */
    fun approveRefund(cmd: OrderApproveRefundCMD): Result<Unit, BusinessError> {
        cmd.validate().onFailure { return Failure(it) }
        val order = orderRepository.findById(cmd.orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.approveRefund(cmd.itemIds).onFailure { return Failure(it) }
        orderRepository.save(order)
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }

    /** 卖家拒绝退款 */
    fun rejectRefund(cmd: OrderRejectRefundCMD): Result<Unit, BusinessError> {
        cmd.validate().onFailure { return Failure(it) }
        val order = orderRepository.findById(cmd.orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.rejectRefund(cmd.rejectReason, cmd.itemIds).onFailure { return Failure(it) }
        orderRepository.save(order)
        order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }
}
