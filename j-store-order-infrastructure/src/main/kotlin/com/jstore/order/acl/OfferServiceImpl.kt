package com.jstore.order.acl

import com.jstore.shop.api.OfferSnapshotQueryService

class OfferServiceImpl(private val queryService: OfferSnapshotQueryService) : OfferService {
    override fun queryOffers(offerIds: List<Long>): List<OfferInfo> =
        queryService.queryOffers(offerIds).map {
            OfferInfo(
                offerId = it.offerId,
                storeId = it.storeId,
                merchantId = it.merchantId,
                skuId = it.skuId,
                channelId = it.channelId,
                market = it.market,
                price = it.price,
                version = it.offerVersion,
                fulfillmentNodeId = it.fulfillmentNodeId,
                allowBackorder = it.allowBackorder,
            )
        }
}
