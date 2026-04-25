package com.jstore.order.domain.order.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface OrderPOJpaRepository : JpaRepository<OrderPO, Long> {

    fun findByBuyerUid(buyerUid: Long): List<OrderPO>

    fun findByBuyerUid(buyerUid: Long, pageable: Pageable): Page<OrderPO>
}
