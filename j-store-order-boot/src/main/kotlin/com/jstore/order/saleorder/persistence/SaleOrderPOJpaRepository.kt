package com.jstore.com.jstore.order.saleorder.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SaleOrderPOJpaRepository: JpaRepository<SaleOrderPO, Long> {
    fun findSaleOrderPOSByUid(uid: Long): MutableList<SaleOrderPO>
    fun findAllByUidOrderByCreateTimeDesc(uid: Long, pageable: Pageable): Page<SaleOrderPO>

}