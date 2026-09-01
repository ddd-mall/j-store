package com.jstore.cart.domain

import com.jstore.cart.domain.persistence.*
import com.jstore.common.properties.Price
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class CartRepositoryImpl(private val jpa: CartPOJpaRepository) : CartRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregate: Cart): Cart = toDomain(jpa.save(toPO(aggregate)))
    override fun findById(id: CartId): Cart? = jpa.findById(id.value).orElse(null)?.let(::toDomain)
    override fun findActiveByBuyerId(buyerId: BuyerId): Cart? = jpa.findByBuyerIdAndStatus(buyerId.value, CartStatus.ACTIVE)?.let(::toDomain)

    private fun toPO(cart: Cart): CartPO {
        val po = CartPO(cart.id.value, cart.buyerId.value, cart.status, cart.settlementScope.market, cart.settlementScope.channelId, cart.settlementScope.currency, cart.contentVersion, cart.persistenceVersion)
        po.lines = cart.lines.map { CartLinePO(it.id.value, po, it.skuId.value, it.offerId.value, it.merchantId.value, it.quantity, it.selected, it.addedAt, it.modifiedAt) }.toMutableList()
        return po
    }
    private fun toDomain(po: CartPO) = Cart(CartId(po.id), BuyerId(po.buyerId), SettlementScope(po.market, po.channelId, po.currency), po.status, po.lines.map { CartLine(CartLineId(it.id), SkuId(it.skuId), OfferId(it.offerId), MerchantId(it.merchantId), it.quantity, it.selected, it.addedAt, it.modifiedAt) }, po.contentVersion, po.persistenceVersion)
}

@Repository
class CartAssessmentRepositoryImpl(private val jpa: CartAssessmentPOJpaRepository) : CartAssessmentRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregate: CartAssessment): CartAssessment = toDomain(jpa.save(toPO(aggregate)))
    override fun findById(id: CartAssessmentId): CartAssessment? = jpa.findById(id.value).orElse(null)?.let(::toDomain)
    override fun findByCartAndVersion(cartId: CartId, version: Long) = jpa.findByCartIdAndSourceCartVersion(cartId.value, version)?.let(::toDomain)
    override fun findLatestByCart(cartId: CartId) = jpa.findFirstByCartIdOrderBySourceCartVersionDesc(cartId.value)?.let(::toDomain)
    private fun toPO(a: CartAssessment): CartAssessmentPO {
        val po = CartAssessmentPO(a.id.value, a.cartId.value, a.sourceCartVersion, a.status, a.estimatedAmount.fen, a.currency, a.evaluatedAt)
        po.lines = a.lines.map { CartAssessmentLinePO(0, po, it.cartLineId.value, it.status, it.observedUnitPrice?.fen, it.observedOfferVersion, it.observedCatalogVersion, it.observedAtp, it.amount.fen) }.toMutableList()
        return po
    }
    private fun toDomain(po: CartAssessmentPO) = CartAssessment(CartAssessmentId(po.id), CartId(po.cartId), po.sourceCartVersion, po.status, Price.ofFen(po.amountFen), po.currency, po.evaluatedAt, po.lines.map { CartAssessmentLine(CartLineId(it.cartLineId), it.status, it.unitPriceFen?.let(Price::ofFen), it.offerVersion, it.catalogVersion, it.observedAtp, Price.ofFen(it.amountFen)) })
}

@Repository
class CartRequestReceiptRepositoryImpl(private val jpa: CartRequestReceiptPOJpaRepository) : CartRequestReceiptRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregate: CartRequestReceipt): CartRequestReceipt = toDomain(jpa.save(CartRequestReceiptPO(aggregate.id.value, aggregate.buyerId.value, aggregate.requestId, aggregate.requestDigest, aggregate.cartId.value, aggregate.cartVersion)))
    override fun findById(id: CartRequestReceiptId) = jpa.findById(id.value).orElse(null)?.let(::toDomain)
    override fun findByBuyerAndRequest(buyerId: BuyerId, requestId: String) = jpa.findByBuyerIdAndRequestId(buyerId.value, requestId)?.let(::toDomain)
    private fun toDomain(po: CartRequestReceiptPO) = CartRequestReceipt(CartRequestReceiptId(po.id), BuyerId(po.buyerId), po.requestId, po.requestDigest, CartId(po.cartId), po.cartVersion)
}
