package com.jstore.fulfillment.domain

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.framework.RecordsDomainEvents
import com.jstore.common.properties.Id
import com.jstore.common.utils.Result
import java.time.Instant

data class FulfillmentOrderId(override val value: Long) : Id<Long>(value)

enum class FulfillmentOrderStatus {
    PENDING,
    READY,
    SHIPPED,
    DELIVERED,
}

data class ShippingRecipient(
    val name: String,
    val phone: String?,
    val email: String?,
    val countryCode: String,
    val districtCode: String,
    val detailAddress: String?,
)

data class FulfillmentItem(
    val orderItemId: Long,
    val skuId: Long,
    val quantity: Int,
) {
    init {
        require(orderItemId > 0 && skuId > 0 && quantity > 0)
    }
}

interface FulfillmentOrder : AggregateRoot<FulfillmentOrderId>, RecordsDomainEvents {
    val orderId: Long
    val merchantId: Long
    val status: FulfillmentOrderStatus
    val recipient: ShippingRecipient
    val items: List<FulfillmentItem>
    val carrierCode: String?
    val trackingNumber: String?

    fun prepare(occurredAt: Instant): Result<Boolean, BusinessError>

    fun dispatch(
        carrierCode: String,
        trackingNumber: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    fun deliver(occurredAt: Instant): Result<Boolean, BusinessError>
}
