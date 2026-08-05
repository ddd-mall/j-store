package com.jstore.warehouse.domain.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.warehouse.domain.PhysicalStockId
import java.time.Instant

@DomainEventType(name = "warehouse.physical-stock-changed", version = 1)
data class PhysicalStockChangedEvent(
    val stockId: PhysicalStockId,
    val skuId: Long,
    val fulfillmentNodeId: String,
    val onHand: Int,
    val sourceVersion: Long,
    val reason: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "warehouse.physical-stock-changed"
    override val eventVersion = 1
    override val aggregateType = "PhysicalStock"
    override val aggregateId = stockId.value
}
