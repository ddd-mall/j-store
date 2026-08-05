package com.jstore.fulfillment.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.fulfillment.domain.FulfillmentOrder
import java.time.Instant

interface FulfillmentUseCase {
    fun createForOrder(request: FulfillmentRequest): Result<FulfillmentOrder, BusinessError>
    fun getByOrderId(orderId: Long): Result<FulfillmentOrder, BusinessError>
    fun prepare(orderId: Long, occurredAt: Instant = Instant.now()): Result<Boolean, BusinessError>
    fun dispatch(orderId: Long, carrierCode: String, trackingNumber: String, occurredAt: Instant = Instant.now()): Result<Boolean, BusinessError>
    fun deliver(orderId: Long, occurredAt: Instant = Instant.now()): Result<Boolean, BusinessError>
}
