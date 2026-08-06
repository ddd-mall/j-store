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
package com.jstore.fulfillment.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onSuccess
import com.jstore.fulfillment.domain.FulfillmentErrors
import com.jstore.fulfillment.domain.FulfillmentItem
import com.jstore.fulfillment.domain.FulfillmentOrder
import com.jstore.fulfillment.domain.FulfillmentOrderId
import com.jstore.fulfillment.domain.FulfillmentOrderImpl
import com.jstore.fulfillment.domain.FulfillmentOrderRepository
import com.jstore.fulfillment.domain.ShippingRecipient
import java.time.Instant

data class FulfillmentRequest(
    val orderId: Long,
    val merchantId: Long,
    val recipient: ShippingRecipient,
    val items: List<FulfillmentItem>,
)

class FulfillmentApplicationService(
    private val repository: FulfillmentOrderRepository,
    private val sequence: SnowFlakSequence,
    private val publisher: DomainEventPublisher,
) : FulfillmentUseCase {
    override fun createForOrder(
        request: FulfillmentRequest
    ): Result<FulfillmentOrder, BusinessError> {
        repository.findByOrderId(request.orderId)?.let { existing ->
            return if (
                existing.merchantId == request.merchantId &&
                    existing.recipient == request.recipient &&
                    existing.items == request.items
            )
                Success(existing)
            else Failure(FulfillmentErrors.ORDER_CONFLICT)
        }
        val fulfillment =
            FulfillmentOrderImpl(
                id = FulfillmentOrderId(sequence.nextId()),
                orderId = request.orderId,
                merchantId = request.merchantId,
                recipient = request.recipient,
                items = request.items,
            )
        repository.save(fulfillment)
        fulfillment.publishPendingEvents(publisher)
        return Success(fulfillment)
    }

    override fun getByOrderId(orderId: Long): Result<FulfillmentOrder, BusinessError> =
        repository.findByOrderId(orderId)?.let(::Success) ?: Failure(FulfillmentErrors.NOT_FOUND)

    override fun prepare(
        orderId: Long,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> = mutate(orderId) { it.prepare(occurredAt) }

    override fun dispatch(
        orderId: Long,
        carrierCode: String,
        trackingNumber: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> =
        mutate(orderId) {
            it.dispatch(carrierCode, trackingNumber, occurredAt)
        }

    override fun deliver(
        orderId: Long,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> = mutate(orderId) { it.deliver(occurredAt) }

    private fun mutate(
        orderId: Long,
        operation: (FulfillmentOrder) -> Result<Boolean, BusinessError>,
    ): Result<Boolean, BusinessError> {
        val fulfillment =
            repository.findByOrderId(orderId) ?: return Failure(FulfillmentErrors.NOT_FOUND)
        return operation(fulfillment).onSuccess { changed ->
            if (changed) {
                repository.save(fulfillment)
                fulfillment.publishPendingEvents(publisher)
            }
        }
    }
}
