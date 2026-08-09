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
package com.jstore.shop.domain.offer.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.shop.domain.offer.SaleAuthorizationId
import java.time.Instant

@DomainEventType(name = "store.sale-authorized", version = 1)
data class AuthorizedSaleLine(
    val authorizationId: String,
    val offerId: Long,
    val skuId: Long,
    val quantity: Int,
    val fulfillmentNodeId: String,
    val expiresAt: Instant,
)

data class SaleAuthorizedEvent(
    val orderId: Long,
    val items: List<AuthorizedSaleLine>,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "store.sale-authorized"
    override val eventVersion = 1
    override val aggregateType = "OrderSaleAuthorization"
    override val aggregateId = orderId.toString()
}

@DomainEventType(name = "store.sale-authorization-released", version = 1)
data class SaleAuthorizationReleasedEvent(
    val authorizationId: SaleAuthorizationId,
    val orderId: Long,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "store.sale-authorization-released"
    override val eventVersion = 1
    override val aggregateType = "SaleAuthorization"
    override val aggregateId = authorizationId.value
}

@DomainEventType(name = "store.sale-authorization-rejected", version = 1)
data class SaleAuthorizationRejectedEvent(
    val orderId: Long,
    val reason: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "store.sale-authorization-rejected"
    override val eventVersion = 1
    override val aggregateType = "OrderSaleAuthorization"
    override val aggregateId = orderId.toString()
}
