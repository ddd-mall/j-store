package com.jstore.order.domain.order.command

import com.jstore.common.errors.CommonErrors.OBJECT_NOT_FOUND
import com.jstore.order.domain.order.OrderImpl
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository
import org.springframework.stereotype.Service

class OrderShippingCMD(
    val orderId: OrderId,
)

@Service
class OrderShippingHandler(
    private val orderRepository: OrderRepository,
) {
    fun handle(cmd: OrderShippingCMD): OrderImpl {
        val order = orderRepository.findById(cmd.orderId) ?: throw OBJECT_NOT_FOUND
        order.shipping()
        return orderRepository.save(order)
    }
}