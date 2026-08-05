package com.jstore.order.acl

import com.jstore.common.properties.Price

fun interface OfferService {
    fun queryOffers(offerIds: List<Long>): List<OfferInfo>
}

data class OfferInfo(
    val offerId: Long,
    val storeId: Long,
    val merchantId: Long,
    val skuId: Long,
    val channelId: String,
    val market: String,
    val price: Price,
    val version: Long,
    val fulfillmentNodeId: String,
    val allowBackorder: Boolean,
)
