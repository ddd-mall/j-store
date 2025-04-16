package com.jstore.com.jstore.order.domain.order.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderItemPOJpaRepository: JpaRepository<OrderItemPO, Long> {
    fun findAllByOrderIdIsIn(orderIds: Collection<Long>): List<OrderItemPO>
    fun findAllByOrderId(orderId: Long): List<OrderItemPO>
}