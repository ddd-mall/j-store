/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.fulfillment.domain

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.EventRecordingAggregateRoot
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.fulfillment.domain.event.FulfillmentPreparedEvent
import com.jstore.fulfillment.domain.event.ShipmentDeliveredEvent
import com.jstore.fulfillment.domain.event.ShipmentDispatchedEvent
import java.time.Instant

class FulfillmentOrderImpl(
    override val id: FulfillmentOrderId,
    override val orderId: Long,
    override val merchantId: Long,
    override val recipient: ShippingRecipient,
    items: List<FulfillmentItem>,
    private var _status: FulfillmentOrderStatus = FulfillmentOrderStatus.PENDING,
    private var _carrierCode: String? = null,
    private var _trackingNumber: String? = null,
) : EventRecordingAggregateRoot<FulfillmentOrderId>(), FulfillmentOrder {
    private val _items = items.toList()
    override val status: FulfillmentOrderStatus
        get() = _status

    override val items: List<FulfillmentItem>
        get() = _items.toList()

    override val carrierCode: String?
        get() = _carrierCode

    override val trackingNumber: String?
        get() = _trackingNumber

    init {
        require(orderId > 0 && merchantId > 0 && _items.isNotEmpty())
        require(_items.map { it.orderItemId }.toSet().size == _items.size)
    }

    override fun prepare(occurredAt: Instant): Result<Boolean, BusinessError> {
        if (_status == FulfillmentOrderStatus.READY) return Success(false)
        if (_status != FulfillmentOrderStatus.PENDING)
            return Failure(FulfillmentErrors.INVALID_STATE)
        _status = FulfillmentOrderStatus.READY
        raise(FulfillmentPreparedEvent(id, orderId, occurredAt))
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
        raise(
            ShipmentDispatchedEvent(id, orderId, normalizedCarrier, normalizedTracking, occurredAt)
        )
        return Success(true)
    }

    override fun deliver(occurredAt: Instant): Result<Boolean, BusinessError> {
        if (_status == FulfillmentOrderStatus.DELIVERED) return Success(false)
        if (_status != FulfillmentOrderStatus.SHIPPED)
            return Failure(FulfillmentErrors.INVALID_STATE)
        _status = FulfillmentOrderStatus.DELIVERED
        raise(ShipmentDeliveredEvent(id, orderId, occurredAt))
        return Success(true)
    }
}
