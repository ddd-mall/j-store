package com.jstore.order.domain.aftersale

import com.jstore.common.properties.Price
import com.jstore.order.domain.order.FulfillmentStatus
import com.jstore.order.domain.order.OrderItemId
import java.time.LocalDateTime

enum class RefundCategory {
    NO_LONGER_NEEDED,
    NOT_AS_DESCRIBED,
    QUALITY_ISSUE,
    OTHER,
}

data class RefundReason(val category: RefundCategory, val description: String) {
    init {
        require(description.isNotBlank() && description.length <= 500)
    }
}

data class FulfillmentSnapshot(val status: FulfillmentStatus, val requireReturn: Boolean) {
    init {
        require(
            requireReturn ==
                (status == FulfillmentStatus.SHIPPED || status == FulfillmentStatus.DELIVERED)
        )
    }
}

data class GoodsSnapshot(
    val skuId: Long,
    val spuId: Long,
    val goodsName: String,
    val skuDescription: String,
)

data class RefundEligibilitySnapshot(
    val orderItemId: OrderItemId,
    val refundableQuantity: Int,
    val refundableAmount: Price,
    val currency: String,
    val goods: GoodsSnapshot,
) {
    init {
        require(refundableQuantity > 0)
        require(refundableAmount > Price.ZERO)
        require(currency == "CNY")
    }
}

data class ReviewDecision(
    val reviewerId: MerchantActorId,
    val reviewedAt: LocalDateTime,
    val rejectionReason: String?,
)
