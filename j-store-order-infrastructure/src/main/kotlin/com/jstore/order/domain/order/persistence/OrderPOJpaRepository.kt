package com.jstore.order.domain.order.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OrderPOJpaRepository : JpaRepository<OrderPO, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderPO o where o.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): OrderPO?

    fun findByBuyerUid(buyerUid: Long): List<OrderPO>

    fun findByBuyerUid(buyerUid: Long, pageable: Pageable): Page<OrderPO>
}
