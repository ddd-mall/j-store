package com.jstore.cart.domain.persistence

import com.jstore.cart.domain.CartStatus
import org.springframework.data.jpa.repository.JpaRepository

interface CartPOJpaRepository : JpaRepository<CartPO, Long> { fun findByBuyerIdAndStatus(buyerId: Long, status: CartStatus): CartPO? }
interface CartAssessmentPOJpaRepository : JpaRepository<CartAssessmentPO, Long> {
    fun findByCartIdAndSourceCartVersion(cartId: Long, sourceCartVersion: Long): CartAssessmentPO?
    fun findFirstByCartIdOrderBySourceCartVersionDesc(cartId: Long): CartAssessmentPO?
}
interface CartRequestReceiptPOJpaRepository : JpaRepository<CartRequestReceiptPO, String> { fun findByBuyerIdAndRequestId(buyerId: Long, requestId: String): CartRequestReceiptPO? }
