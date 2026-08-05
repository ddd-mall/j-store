package com.jstore.warehouse.domain.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface PhysicalStockPOJpaRepository : JpaRepository<PhysicalStockPO, String>
