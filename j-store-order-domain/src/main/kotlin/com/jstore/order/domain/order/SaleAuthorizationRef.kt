package com.jstore.order.domain.order

import java.time.Instant

data class SaleAuthorizationRef(
    val authorizationId: String,
    val offerId: Long,
    val expiresAt: Instant,
) {
    init {
        require(authorizationId.isNotBlank() && offerId > 0)
    }
}
