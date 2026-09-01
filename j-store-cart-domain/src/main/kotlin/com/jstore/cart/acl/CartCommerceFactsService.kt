package com.jstore.cart.acl

import com.jstore.cart.domain.CartLine
import com.jstore.cart.domain.CartLineCommerceFacts
import com.jstore.cart.domain.OfferId
import com.jstore.cart.domain.SettlementScope
import com.jstore.cart.domain.SkuId

data class OfferIdentity(
    val offerId: OfferId,
    val skuId: SkuId,
    val merchantId: Long,
    val settlementScope: SettlementScope,
)

interface CartCommerceFactsService {
    fun findOffer(offerId: OfferId): OfferIdentity?
    fun collect(lines: List<CartLine>): List<CartLineCommerceFacts>
}
