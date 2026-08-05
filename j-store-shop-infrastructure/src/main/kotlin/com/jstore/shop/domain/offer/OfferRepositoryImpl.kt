package com.jstore.shop.domain.offer

import com.jstore.common.properties.Price
import com.jstore.shop.domain.offer.persistence.SaleAuthorizationPO
import com.jstore.shop.domain.offer.persistence.SaleAuthorizationPOJpaRepository
import com.jstore.shop.domain.offer.persistence.SalesOfferPO
import com.jstore.shop.domain.offer.persistence.SalesOfferPOJpaRepository
import com.jstore.shop.domain.offer.persistence.StorePO
import com.jstore.shop.domain.offer.persistence.StorePOJpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class StoreRepositoryImpl(private val jpa: StorePOJpaRepository) : StoreRepository, StoreGuard {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: Store): Store = toDomain(jpa.save(toPO(entity)))

    override fun findById(id: StoreId): Store? = jpa.findById(id.value).orElse(null)?.let(::toDomain)

    @Transactional(propagation = Propagation.MANDATORY)
    override fun lock(ids: List<StoreId>): List<Store> =
        jpa.findAllByIdForUpdate(ids.map { it.value }.sorted()).map(::toDomain)

    private fun toPO(store: Store) =
        StorePO(store.id.value, store.merchantId.value, store.name, store.status, store.persistenceVersion)

    private fun toDomain(po: StorePO) =
        Store(StoreId(po.id), MerchantId(po.merchantId), po.name, po.status, po.persistenceVersion)
}

@Repository
class SalesOfferRepositoryImpl(private val jpa: SalesOfferPOJpaRepository) :
    SalesOfferRepository, SalesOfferGuard {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: SalesOffer): SalesOffer = toDomain(jpa.save(toPO(entity)))

    override fun findById(id: SalesOfferId): SalesOffer? =
        jpa.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findAllByIds(ids: List<SalesOfferId>): List<SalesOffer> =
        jpa.findAllById(ids.map { it.value }).map(::toDomain)

    @Transactional(propagation = Propagation.MANDATORY)
    override fun lock(ids: List<SalesOfferId>): List<SalesOffer> =
        jpa.findAllByIdForUpdate(ids.map { it.value }.sorted()).map(::toDomain)

    private fun toPO(offer: SalesOffer) =
        SalesOfferPO(
            id = offer.id.value,
            storeId = offer.storeId.value,
            merchantId = offer.merchantId.value,
            skuId = offer.skuId.value,
            channelId = offer.channel.channelId,
            market = offer.channel.market,
            priceFen = offer.price.fen,
            status = offer.status,
            startsAt = offer.effectivePeriod.startsAt,
            endsAt = offer.effectivePeriod.endsAt,
            maxQuantityPerOrder = offer.purchaseLimit.maxQuantityPerOrder,
            fulfillmentNodeId = offer.fulfillmentPolicy.preferredNodeId.value,
            allowBackorder = offer.fulfillmentPolicy.allowBackorder,
            offerVersion = offer.version,
            persistenceVersion = offer.persistenceVersion,
        )

    private fun toDomain(po: SalesOfferPO) =
        SalesOffer(
            SalesOfferId(po.id),
            StoreId(po.storeId),
            MerchantId(po.merchantId),
            SkuId(po.skuId),
            Channel(po.channelId, po.market),
            Price.ofFen(po.priceFen),
            po.status,
            EffectivePeriod(po.startsAt, po.endsAt),
            PurchaseLimit(po.maxQuantityPerOrder),
            FulfillmentPolicy(FulfillmentNodeId(po.fulfillmentNodeId), po.allowBackorder),
            po.offerVersion,
            po.persistenceVersion,
        )
}

@Repository
class SaleAuthorizationRepositoryImpl(private val jpa: SaleAuthorizationPOJpaRepository) :
    SaleAuthorizationRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: SaleAuthorization): SaleAuthorization = toDomain(jpa.save(toPO(entity)))

    override fun findById(id: SaleAuthorizationId): SaleAuthorization? =
        jpa.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findByOrderId(orderId: Long): List<SaleAuthorization> =
        jpa.findAllByOrderIdOrderByOfferId(orderId).map(::toDomain)

    private fun toPO(auth: SaleAuthorization) =
        SaleAuthorizationPO(
            id = auth.id.value,
            orderId = auth.orderId,
            offerId = auth.offerId.value,
            storeId = auth.storeId.value,
            merchantId = auth.merchantId.value,
            skuId = auth.skuId.value,
            quantity = auth.quantity,
            offerVersion = auth.offerVersion,
            unitPriceFen = auth.unitPrice.fen,
            fulfillmentNodeId = auth.fulfillmentPolicy.preferredNodeId.value,
            allowBackorder = auth.fulfillmentPolicy.allowBackorder,
            authorizedAt = auth.authorizedAt,
            expiresAt = auth.expiresAt,
            status = auth.status,
            persistenceVersion = auth.persistenceVersion,
        )

    private fun toDomain(po: SaleAuthorizationPO) =
        SaleAuthorization.reconstitute(
            SaleAuthorizationId(po.id),
            po.orderId,
            SalesOfferId(po.offerId),
            StoreId(po.storeId),
            MerchantId(po.merchantId),
            SkuId(po.skuId),
            po.quantity,
            po.offerVersion,
            Price.ofFen(po.unitPriceFen),
            FulfillmentPolicy(FulfillmentNodeId(po.fulfillmentNodeId), po.allowBackorder),
            po.authorizedAt,
            po.expiresAt,
            po.status,
            po.persistenceVersion,
        )
}
