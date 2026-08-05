package com.jstore.inventory.domain.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StockPositionPOJpaRepository : JpaRepository<StockPositionPO, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from StockPositionPO p where p.id in :ids order by p.id")
    fun findAllByIdForUpdate(@Param("ids") ids: List<String>): List<StockPositionPO>
}

interface StockReservationPOJpaRepository : JpaRepository<StockReservationPO, String> {
    fun findByBusinessKey(businessKey: String): StockReservationPO?

    fun findAllByOrderIdOrderBySkuIdAsc(orderId: Long): List<StockReservationPO>
}
