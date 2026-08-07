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
package com.jstore.goods.domain.commodity.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import com.jstore.goods.domain.commodity.SpuId
import java.time.Instant

@DomainEventType(name = "commodity.on-sale", version = 1)
class CommodityOnSaleEvent(
    override val source: Any,
    val spuId: SpuId,
    /** 上架时的快照版本号 */
    val snapshotVersion: Long,
    override val occurredAt: Instant = Instant.now(),
) : ExplicitDomainEvent {
    override val eventName: String = "commodity.on-sale"
    override val eventVersion: Int = 1
    override val aggregateType: String = "Commodity"
    override val aggregateId: String = spuId.value.toString()
    override val eventId: String =
        stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}
