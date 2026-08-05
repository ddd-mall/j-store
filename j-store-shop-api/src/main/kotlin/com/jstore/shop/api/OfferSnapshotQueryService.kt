package com.jstore.shop.api

import com.jstore.common.properties.Price
import java.time.Instant

data class OfferSnapshotInfo(
    val offerId: Long,
    val storeId: Long,
    val merchantId: Long,
    val skuId: Long,
    val channelId: String,
    val market: String,
    val price: Price,
    val offerVersion: Long,
    val fulfillmentNodeId: String,
    val allowBackorder: Boolean,
    val active: Boolean,
    val startsAt: Instant,
    val endsAt: Instant?,
)

fun interface OfferSnapshotQueryService {
    fun queryOffers(offerIds: List<Long>): List<OfferSnapshotInfo>
}
