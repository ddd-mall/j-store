package com.jstore.cart.domain

import com.jstore.common.framework.AggregateRepository

interface CartRepository : AggregateRepository<CartId, Cart> {
    fun findActiveByBuyerId(buyerId: BuyerId): Cart?
}

interface CartAssessmentRepository : AggregateRepository<CartAssessmentId, CartAssessment> {
    fun findByCartAndVersion(cartId: CartId, version: Long): CartAssessment?
    fun findLatestByCart(cartId: CartId): CartAssessment?
}

data class CartRequestReceipt(
    override val id: CartRequestReceiptId,
    val buyerId: BuyerId,
    val requestId: String,
    val requestDigest: String,
    val cartId: CartId,
    val cartVersion: Long,
) : com.jstore.common.framework.AggregateRoot<CartRequestReceiptId>

interface CartRequestReceiptRepository : AggregateRepository<CartRequestReceiptId, CartRequestReceipt> {
    fun findByBuyerAndRequest(buyerId: BuyerId, requestId: String): CartRequestReceipt?
}
