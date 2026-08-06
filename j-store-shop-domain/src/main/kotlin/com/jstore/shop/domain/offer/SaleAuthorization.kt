package com.jstore.shop.domain.offer

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.EventRecordingAggregateRoot
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.shop.domain.offer.event.SaleAuthorizationReleasedEvent
import java.time.Instant

class SaleAuthorization
private constructor(
    override val id: SaleAuthorizationId,
    val orderId: Long,
    val offerId: SalesOfferId,
    val storeId: StoreId,
    val merchantId: MerchantId,
    val skuId: SkuId,
    val quantity: Int,
    val offerVersion: Long,
    val unitPrice: Price,
    val fulfillmentPolicy: FulfillmentPolicy,
    val authorizedAt: Instant,
    val expiresAt: Instant,
    status: SaleAuthorizationStatus,
    val persistenceVersion: Long,
) : EventRecordingAggregateRoot<SaleAuthorizationId>() {
    private var _status = status

    val status: SaleAuthorizationStatus
        get() = _status

    fun isUsable(now: Instant): Boolean =
        _status == SaleAuthorizationStatus.AUTHORIZED && now.isBefore(expiresAt)

    fun release(now: Instant): Result<Boolean, BusinessError> {
        if (_status == SaleAuthorizationStatus.RELEASED) return Success(false)
        if (_status != SaleAuthorizationStatus.AUTHORIZED) return Failure(OfferErrors.ILLEGAL_STATE)
        _status = SaleAuthorizationStatus.RELEASED
        raise(SaleAuthorizationReleasedEvent(id, orderId, now))
        return Success(true)
    }

    companion object {
        fun authorized(
            id: SaleAuthorizationId,
            orderId: Long,
            offerId: SalesOfferId,
            storeId: StoreId,
            merchantId: MerchantId,
            skuId: SkuId,
            quantity: Int,
            offerVersion: Long,
            unitPrice: Price,
            fulfillmentPolicy: FulfillmentPolicy,
            authorizedAt: Instant,
            expiresAt: Instant,
        ) =
            SaleAuthorization(
                id,
                orderId,
                offerId,
                storeId,
                merchantId,
                skuId,
                quantity,
                offerVersion,
                unitPrice,
                fulfillmentPolicy,
                authorizedAt,
                expiresAt,
                SaleAuthorizationStatus.AUTHORIZED,
                0,
            )

        fun reconstitute(
            id: SaleAuthorizationId,
            orderId: Long,
            offerId: SalesOfferId,
            storeId: StoreId,
            merchantId: MerchantId,
            skuId: SkuId,
            quantity: Int,
            offerVersion: Long,
            unitPrice: Price,
            fulfillmentPolicy: FulfillmentPolicy,
            authorizedAt: Instant,
            expiresAt: Instant,
            status: SaleAuthorizationStatus,
            persistenceVersion: Long = 0,
        ) =
            SaleAuthorization(
                id,
                orderId,
                offerId,
                storeId,
                merchantId,
                skuId,
                quantity,
                offerVersion,
                unitPrice,
                fulfillmentPolicy,
                authorizedAt,
                expiresAt,
                status,
                persistenceVersion,
            )
    }
}
