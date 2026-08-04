package com.jstore.fulfillment.domain

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.fulfillment.domain.event.FulfillmentPreparedEvent
import com.jstore.fulfillment.domain.event.ShipmentDeliveredEvent
import com.jstore.fulfillment.domain.event.ShipmentDispatchedEvent
import java.time.Instant
import java.util.LinkedList
import java.util.Queue

class FulfillmentOrderImpl(
    override val id: FulfillmentOrderId,
    override val orderId: Long,
    override val merchantId: Long,
    override val recipient: ShippingRecipient,
    items: List<FulfillmentItem>,
    private var _status: FulfillmentOrderStatus = FulfillmentOrderStatus.PENDING,
    private var _carrierCode: String? = null,
    private var _trackingNumber: String? = null,
) : FulfillmentOrder {
    override val domainEventQueue: Queue<DomainEvent> = LinkedList()
    private val _items = items.toList()
    override val status: FulfillmentOrderStatus get() = _status
    override val items: List<FulfillmentItem> get() = _items.toList()
    override val carrierCode: String? get() = _carrierCode
    override val trackingNumber: String? get() = _trackingNumber

    init {
        require(orderId > 0 && merchantId > 0 && _items.isNotEmpty())
        require(_items.map { it.orderItemId }.toSet().size == _items.size)
    }

    override fun prepare(occurredAt: Instant): Result<Boolean, BusinessError> {
        if (_status == FulfillmentOrderStatus.READY) return Success(false)
        if (_status != FulfillmentOrderStatus.PENDING) return Failure(FulfillmentErrors.INVALID_STATE)
        _status = FulfillmentOrderStatus.READY
        publishEvent(FulfillmentPreparedEvent(id, orderId, occurredAt))
        return Success(true)
    }

    override fun dispatch(
        carrierCode: String,
        trackingNumber: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        val normalizedCarrier = carrierCode.trim().uppercase()
        val normalizedTracking = trackingNumber.trim()
        if (normalizedCarrier.isBlank() || normalizedTracking.isBlank()) {
            return Failure(FulfillmentErrors.SHIPPING_REFERENCE_INVALID)
        }
        if (_status in setOf(FulfillmentOrderStatus.SHIPPED, FulfillmentOrderStatus.DELIVERED)) {
            return if (_carrierCode == normalizedCarrier && _trackingNumber == normalizedTracking) {
                Success(false)
            } else {
                Failure(FulfillmentErrors.SHIPPING_REFERENCE_CONFLICT)
            }
        }
        if (_status != FulfillmentOrderStatus.READY) return Failure(FulfillmentErrors.INVALID_STATE)
        _carrierCode = normalizedCarrier
        _trackingNumber = normalizedTracking
        _status = FulfillmentOrderStatus.SHIPPED
        publishEvent(ShipmentDispatchedEvent(id, orderId, normalizedCarrier, normalizedTracking, occurredAt))
        return Success(true)
    }

    override fun deliver(occurredAt: Instant): Result<Boolean, BusinessError> {
        if (_status == FulfillmentOrderStatus.DELIVERED) return Success(false)
        if (_status != FulfillmentOrderStatus.SHIPPED) return Failure(FulfillmentErrors.INVALID_STATE)
        _status = FulfillmentOrderStatus.DELIVERED
        publishEvent(ShipmentDeliveredEvent(id, orderId, occurredAt))
        return Success(true)
    }
}
