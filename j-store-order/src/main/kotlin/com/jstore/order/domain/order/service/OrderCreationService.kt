package com.jstore.order.domain.order.service

import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.command.OrderCreateCmd
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class OrderCreationService(
    private val orderRepository: OrderRepository,
    private val orderFactory: OrderFactory,
) {

    @Transactional(
        rollbackFor = [Exception::class],
        propagation = Propagation.REQUIRED
    )
    fun create(cmd: OrderCreateCmd): Order {
        val order = this.orderFactory.create(cmd)
        order.initial()
        return orderRepository.save(order)
    }
}