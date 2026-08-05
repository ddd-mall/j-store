package com.jstore.shop.domain.offer

import com.jstore.common.properties.Id
import java.time.Instant

data class SalesOfferId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class StoreId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class MerchantId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class SkuId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class FulfillmentNodeId(override val value: String) : Id<String>(value) {
    init {
        require(value.isNotBlank())
    }
}

data class SaleAuthorizationId(override val value: String) : Id<String>(value) {
    init {
        require(value.isNotBlank())
    }
}

data class Channel(val channelId: String, val market: String) {
    init {
        require(channelId.isNotBlank() && market.isNotBlank())
    }
}

data class EffectivePeriod(val startsAt: Instant, val endsAt: Instant?) {
    init {
        require(endsAt == null || endsAt.isAfter(startsAt))
    }

    fun contains(now: Instant): Boolean =
        !now.isBefore(startsAt) && (endsAt == null || now.isBefore(endsAt))
}

data class PurchaseLimit(val maxQuantityPerOrder: Int) {
    init {
        require(maxQuantityPerOrder > 0)
    }
}

data class FulfillmentPolicy(
    val preferredNodeId: FulfillmentNodeId,
    val allowBackorder: Boolean,
)

enum class OfferStatus {
    ACTIVE,
    SUSPENDED,
    ENDED,
}

enum class SaleAuthorizationStatus {
    AUTHORIZED,
    RELEASED,
    EXPIRED,
}
