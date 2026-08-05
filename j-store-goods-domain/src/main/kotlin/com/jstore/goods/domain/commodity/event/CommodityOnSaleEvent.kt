package com.jstore.goods.domain.commodity.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.goods.domain.commodity.SpuId
import java.time.Instant

@DomainEventType(name = "commodity.on-sale", version = 1)
data class CommodityOnSaleEvent(
    val spuId: SpuId,
    val snapshotVersion: Long,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "commodity.on-sale"
    override val eventVersion = 1
    override val aggregateType = "Commodity"
    override val aggregateId = spuId.value.toString()
}
