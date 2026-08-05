package com.jstore.shop.domain.offer

import com.jstore.common.framework.AggregateRepository

interface SalesOfferRepository : AggregateRepository<SalesOfferId, SalesOffer> {
    fun findAllByIds(ids: List<SalesOfferId>): List<SalesOffer>
}

fun interface SalesOfferGuard {
    fun lock(ids: List<SalesOfferId>): List<SalesOffer>
}

interface SaleAuthorizationRepository :
    AggregateRepository<SaleAuthorizationId, SaleAuthorization> {
    fun findByOrderId(orderId: Long): List<SaleAuthorization>
}
