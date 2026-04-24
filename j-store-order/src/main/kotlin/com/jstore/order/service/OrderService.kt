package com.jstore.order.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Price
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
import com.jstore.order.domain.order.command.OrderCreateCMD

/**
 * 订单应用服务
 * 编排用例: 加载聚合 → 执行领域行为 → 保存
 * 不包含业务规则，全部委托给领域对象
 */
class OrderService(
    private val orderFactory: OrderFactory,
    private val orderRepository: OrderRepository,
) {

    /** 创建订单 */
    fun createOrder(cmd: OrderCreateCMD): Result<Order, BusinessError> {
        cmd.validate().onFailure { return Failure(it) }
        val order = orderFactory.create(cmd).getOrThrow()
        orderRepository.add(order)
        return Success(order)
    }

    /** 支付回调 */
    fun payOrder(orderId: OrderId, paidAmount: Price): Result<Unit, BusinessError> {
        val order = orderRepository.findById(orderId)
            ?: return Failure(OrderErrors.ORDER_NOT_FOUND)
        order.pay(paidAmount).onFailure { return Failure(it) }
        orderRepository.save(order)
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
}
