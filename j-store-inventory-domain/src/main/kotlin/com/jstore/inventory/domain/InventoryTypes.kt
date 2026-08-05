package com.jstore.inventory.domain

import com.jstore.common.properties.Id

data class StockPositionId(override val value: String) : Id<String>(value) {
    init {
        require(value.isNotBlank())
    }
}

data class StockReservationId(override val value: String) : Id<String>(value) {
    init {
        require(value.isNotBlank())
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

enum class StockReservationStatus {
    RESERVED,
    CONFIRMED,
    RELEASED,
    EXPIRED,
}
