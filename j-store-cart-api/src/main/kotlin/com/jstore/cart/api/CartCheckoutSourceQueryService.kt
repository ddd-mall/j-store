package com.jstore.cart.api

data class CartCheckoutSourceQuery(
    val buyerId: Long,
    val cartId: Long,
    val expectedCartVersion: Long,
)

data class CartCheckoutLine(
    val cartLineId: Long,
    val offerId: Long,
    val offerVersion: Long,
    val spuId: Long,
    val skuId: Long,
    val quantity: Int,
    val catalogSnapshotVersion: Long,
)

data class CartCheckoutSource(
    val cartId: Long,
    val cartVersion: Long,
    val cartDigest: String,
    val market: String,
    val channelId: String,
    val currency: String,
    val eligibleLines: List<CartCheckoutLine>,
)

sealed interface CartCheckoutSourceResult {
    data class Found(val source: CartCheckoutSource) : CartCheckoutSourceResult
    data object NotFound : CartCheckoutSourceResult
    data object VersionConflict : CartCheckoutSourceResult
    data object NoEligibleLines : CartCheckoutSourceResult
    data object Unavailable : CartCheckoutSourceResult
}

fun interface CartCheckoutSourceQueryService {
    fun prepare(query: CartCheckoutSourceQuery): CartCheckoutSourceResult
}
