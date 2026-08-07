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
import java.time.Instant
import java.util.UUID

data class StockRestoreItem(val skuId: Long, val quantity: Int)

@DomainEventType(name = "stock.after-sale-restore-requested", version = 1)
data class AfterSaleStockRestoreRequestedEvent(
    val afterSaleId: Long,
    val orderId: Long,
    val items: List<StockRestoreItem>,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = UUID.randomUUID().toString(),
) : ExplicitDomainEvent {
    override val source: Any
        get() = afterSaleId

    override val eventName = "stock.after-sale-restore-requested"
    override val eventVersion = 1
    override val aggregateType = "Inventory"
    override val aggregateId = afterSaleId.toString()
}
