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
package com.jstore.order.domain.aftersale.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.common.properties.Price
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import java.time.Instant

data class AfterSaleEventItem(
    val orderItemId: OrderItemId,
    val skuId: Long,
    val quantity: Int,
    val amount: Price,
    val currency: String,
)

sealed class AfterSaleDomainEvent(
    open val afterSaleId: AfterSaleId,
    open val orderId: OrderId,
    override val occurredAt: Instant,
    override val eventId: String,
    override val eventName: String,
    override val eventVersion: Int,
) : DomainEvent {

    override val aggregateType = "AfterSale"
    override val aggregateId
        get() = afterSaleId.value.toString()

}

@DomainEventType(name = "after-sale.requested", version = 1)
data class AfterSaleRequestedEvent(
    override val afterSaleId: AfterSaleId,
    override val orderId: OrderId,
    val applicantId: ApplicantActorId,
    val items: List<AfterSaleEventItem>,
    val reason: RefundReason,
    val requireReturn: Boolean,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId, "after-sale.requested", 1)

@DomainEventType(name = "after-sale.approved", version = 1)
data class AfterSaleApprovedEvent(
    override val afterSaleId: AfterSaleId,
    override val orderId: OrderId,
    val merchantId: MerchantActorId,
    val items: List<AfterSaleEventItem>,
    val requireReturn: Boolean,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId, "after-sale.approved", 1)

@DomainEventType(name = "after-sale.return-received", version = 1)
data class AfterSaleReturnReceivedEvent(
    override val afterSaleId: AfterSaleId,
    override val orderId: OrderId,
    val merchantId: MerchantActorId,
    val items: List<AfterSaleEventItem>,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId, "after-sale.return-received", 1)

@DomainEventType(name = "after-sale.refund-requested", version = 1)
data class AfterSaleRefundRequestedEvent(
    override val afterSaleId: AfterSaleId,
    override val orderId: OrderId,
    val merchantId: MerchantActorId,
    val items: List<AfterSaleEventItem>,
    val amount: Price,
    val currency: String,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId, "after-sale.refund-requested", 1)

@DomainEventType(name = "after-sale.refund-succeeded", version = 1)
data class AfterSaleRefundSucceededEvent(
    override val afterSaleId: AfterSaleId,
    override val orderId: OrderId,
    val refundId: String,
    val items: List<AfterSaleEventItem>,
    val amount: Price,
    val currency: String,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId, "after-sale.refund-succeeded", 1)

@DomainEventType(name = "after-sale.refund-failed", version = 1)
data class AfterSaleRefundFailedEvent(
    override val afterSaleId: AfterSaleId,
    override val orderId: OrderId,
    val refundId: String,
    val reason: String,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId, "after-sale.refund-failed", 1)

@DomainEventType(name = "after-sale.rejected", version = 1)
data class AfterSaleRejectedEvent(
    override val afterSaleId: AfterSaleId,
    override val orderId: OrderId,
    val merchantId: MerchantActorId,
    val rejectionReason: String,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId, "after-sale.rejected", 1)

@DomainEventType(name = "after-sale.cancelled", version = 1)
data class AfterSaleCancelledEvent(
    override val afterSaleId: AfterSaleId,
    override val orderId: OrderId,
    val applicantId: ApplicantActorId,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId, "after-sale.cancelled", 1)
