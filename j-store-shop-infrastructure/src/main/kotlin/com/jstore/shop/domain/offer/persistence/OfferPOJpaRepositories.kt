package com.jstore.shop.domain.offer.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StorePOJpaRepository : JpaRepository<StorePO, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StorePO s where s.id in :ids order by s.id")
    fun findAllByIdForUpdate(@Param("ids") ids: List<Long>): List<StorePO>
}

interface SalesOfferPOJpaRepository : JpaRepository<SalesOfferPO, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOfferPO o where o.id in :ids order by o.id")
    fun findAllByIdForUpdate(@Param("ids") ids: List<Long>): List<SalesOfferPO>
}

interface SaleAuthorizationPOJpaRepository : JpaRepository<SaleAuthorizationPO, String> {
    fun findAllByOrderIdOrderByOfferId(orderId: Long): List<SaleAuthorizationPO>
}
