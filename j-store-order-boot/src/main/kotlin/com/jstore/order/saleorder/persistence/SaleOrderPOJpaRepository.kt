package com.jstore.com.jstore.order.saleorder.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SaleOrderPOJpaRepository: JpaRepository<SaleOrderPO, Long>