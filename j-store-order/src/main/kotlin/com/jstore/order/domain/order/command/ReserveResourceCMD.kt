package com.jstore.order.domain.order.command

import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository

class ReserveResourceCMD(
    val orderId: OrderId
)

class ReserveResourceCMDHandler(
    val orderRepository: OrderRepository
) {
    fun handle(command: ReserveResourceCMD) {
        val order = orderRepository.findById(command.orderId)
        if (order == null) {
            throw OrderErrors.ORDER_DOES_NOT_EXIST
        }

    }
}