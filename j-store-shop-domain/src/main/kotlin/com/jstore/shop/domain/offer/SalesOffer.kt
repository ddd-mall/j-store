package com.jstore.shop.domain.offer

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.Duration
import java.time.Instant

class SalesOffer(
    override val id: SalesOfferId,
    val storeId: StoreId,
    val merchantId: MerchantId,
    val skuId: SkuId,
    val channel: Channel,
    price: Price,
    status: OfferStatus,
    val effectivePeriod: EffectivePeriod,
    val purchaseLimit: PurchaseLimit,
    val fulfillmentPolicy: FulfillmentPolicy,
    version: Long,
    val persistenceVersion: Long = 0,
) : AggregateRoot<SalesOfferId> {
    private var _price = price
    private var _status = status
    private var _version = version

    val price: Price
        get() = _price

    val status: OfferStatus
        get() = _status

    val version: Long
        get() = _version

    init {
        require(price > Price.ZERO && version > 0)
    }

    fun activate(): Result<Unit, BusinessError> {
        if (_status == OfferStatus.ENDED) return Failure(OfferErrors.ILLEGAL_STATE)
        if (_status != OfferStatus.ACTIVE) {
            _status = OfferStatus.ACTIVE
            _version++
        }
        return Success(Unit)
    }

    fun suspend(): Result<Unit, BusinessError> {
        if (_status != OfferStatus.ACTIVE) return Failure(OfferErrors.ILLEGAL_STATE)
        _status = OfferStatus.SUSPENDED
        _version++
        return Success(Unit)
    }

    fun end(): Result<Unit, BusinessError> {
        if (_status == OfferStatus.ENDED) return Failure(OfferErrors.ILLEGAL_STATE)
        _status = OfferStatus.ENDED
        _version++
        return Success(Unit)
    }

    fun changePrice(newPrice: Price): Result<Unit, BusinessError> {
        if (_status == OfferStatus.ENDED || newPrice <= Price.ZERO) {
            return Failure(OfferErrors.ILLEGAL_STATE)
        }
        if (_price != newPrice) {
            _price = newPrice
            _version++
        }
        return Success(Unit)
    }

    fun authorize(
        orderId: Long,
        quantity: Int,
        expectedPriceFen: Long,
        now: Instant,
        expectedVersion: Long = version,
        ttl: Duration = Duration.ofMinutes(15),
    ): Result<SaleAuthorization, BusinessError> {
        if (_status != OfferStatus.ACTIVE) return Failure(OfferErrors.NOT_ACTIVE)
        if (!effectivePeriod.contains(now)) return Failure(OfferErrors.OUTSIDE_EFFECTIVE_PERIOD)
        if (_version != expectedVersion) return Failure(OfferErrors.VERSION_MISMATCH)
        if (_price.fen != expectedPriceFen) return Failure(OfferErrors.PRICE_MISMATCH)
        if (quantity !in 1..purchaseLimit.maxQuantityPerOrder) {
            return Failure(OfferErrors.PURCHASE_LIMIT_EXCEEDED)
        }
        return Success(
            SaleAuthorization.authorized(
                id = SaleAuthorizationId("ORDER-$orderId-OFFER-${id.value}"),
                orderId = orderId,
                offerId = id,
                storeId = storeId,
                merchantId = merchantId,
                skuId = skuId,
                quantity = quantity,
                offerVersion = _version,
                unitPrice = _price,
                fulfillmentPolicy = fulfillmentPolicy,
                authorizedAt = now,
                expiresAt = now.plus(ttl),
            )
        )
    }
}
