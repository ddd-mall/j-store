package com.jstore.order.domain.order.command

import com.jstore.common.errors.CommonErrors
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository
import org.springframework.stereotype.Service

class OrderCancelCMD(
    val orderId: OrderId,
)

@Service
class OrderCancelHandler(
    private val orderRepository: OrderRepository
) {
    fun handle(cmd: OrderCancelCMD) {
        val order = orderRepository.findById(cmd.orderId)
            ?: throw CommonErrors.OBJECT_NOT_FOUND.msg("Order ${cmd.orderId} not found")
        order.cancel()
        orderRepository.save(order)
    }
}