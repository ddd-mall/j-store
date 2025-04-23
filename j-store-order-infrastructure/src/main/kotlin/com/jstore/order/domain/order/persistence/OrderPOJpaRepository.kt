package com.jstore.order.domain.order.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderPOJpaRepository: JpaRepository<OrderPO, Long> {
    fun findOrderPOSByUid(uid: Long): MutableList<OrderPO>
    fun findAllByUidOrderByCreateTimeDesc(uid: Long, pageable: Pageable): Page<OrderPO>

}