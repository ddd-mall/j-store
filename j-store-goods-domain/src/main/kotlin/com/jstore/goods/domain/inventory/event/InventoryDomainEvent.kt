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
package com.jstore.goods.domain.inventory.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import java.time.Instant

/** 库存领域事件基类 */
sealed class InventoryDomainEvent(open val occurredAt: Instant = Instant.now()) : DomainEvent {
    open override val source: Any
        get() = this::class.simpleName ?: "InventoryEvent"
}

/** 库存预扣成功事件 */
@DomainEventType(name = "inventory.stock-reserved", version = 1)
data class StockReservedEvent(
    val orderId: Long,
    override val occurredAt: Instant = Instant.now(),
) : InventoryDomainEvent(occurredAt), ExplicitDomainEvent {
    override val source: Any
        get() = orderId

    override val eventName: String
        get() = "inventory.stock-reserved"

    override val eventVersion: Int
        get() = 1

    override val aggregateType: String
        get() = "InventoryReservation"

    override val aggregateId: String
        get() = orderId.toString()

    override val eventId: String
        get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}

/** 库存预扣失败事件 */
@DomainEventType(name = "inventory.stock-reservation-failed", version = 1)
data class StockReservationFailedEvent(
    val orderId: Long,
    val reason: String,
    override val occurredAt: Instant = Instant.now(),
) : InventoryDomainEvent(occurredAt), ExplicitDomainEvent {
    override val source: Any
        get() = orderId

    override val eventName: String
        get() = "inventory.stock-reservation-failed"

    override val eventVersion: Int
        get() = 1

    override val aggregateType: String
        get() = "InventoryReservation"

    override val aggregateId: String
        get() = orderId.toString()

    override val eventId: String
        get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}
