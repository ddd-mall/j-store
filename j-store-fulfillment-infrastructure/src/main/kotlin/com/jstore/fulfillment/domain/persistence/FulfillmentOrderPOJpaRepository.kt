package com.jstore.fulfillment.domain.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface FulfillmentOrderPOJpaRepository : JpaRepository<FulfillmentOrderPO, Long> {
    fun findByOrderId(orderId: Long): FulfillmentOrderPO?
}
