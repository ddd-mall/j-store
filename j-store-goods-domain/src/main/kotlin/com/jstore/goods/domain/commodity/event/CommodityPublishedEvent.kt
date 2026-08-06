package com.jstore.goods.domain.commodity.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.goods.domain.commodity.SpuId
import java.time.Instant

@DomainEventType(name = "commodity.published", version = 2)
data class CommodityPublishedEvent(
    val spuId: SpuId,
    val snapshotVersion: Long,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "commodity.published"
    override val eventVersion = 2
    override val aggregateType = "Commodity"
    override val aggregateId = spuId.value.toString()
}
