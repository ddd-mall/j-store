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
package com.jstore.goods.acl.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import java.time.Instant

/** 库存上下文 ACL 集成事件：请求预扣库存 由外部上下文（如订单）触发，库存上下文只关心"有人要求预扣"这个信号 */
@DomainEventType(name = "inventory.stock-reservation-requested", version = 1)
data class StockReservationRequestedEvent(
    val orderId: Long,
    val items: List<ReservationItem>,
    override val occurredAt: Instant = Instant.now(),
) : ExplicitDomainEvent {
    override val source: Any
        get() = orderId

    override val eventName: String
        get() = "inventory.stock-reservation-requested"

    override val eventVersion: Int
        get() = 1

    override val aggregateType: String
        get() = "InventoryReservation"

    override val aggregateId: String
        get() = orderId.toString()

    override val eventId: String
        get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}

data class ReservationItem(
    val skuId: Long,
    val quantity: Int,
)
