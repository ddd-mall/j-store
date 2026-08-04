package com.jstore.payment.domain.payment.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PaymentOrderPOJpaRepository : JpaRepository<PaymentOrderPO, Long> {
    fun findByOrderId(orderId: Long): PaymentOrderPO?

    @Query("select distinct p from PaymentOrderPO p join p.refunds r where r.id = :refundId")
    fun findByRefundId(refundId: Long): PaymentOrderPO?
}
