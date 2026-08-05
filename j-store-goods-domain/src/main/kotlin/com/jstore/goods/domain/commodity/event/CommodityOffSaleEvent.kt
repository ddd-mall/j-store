package com.jstore.goods.domain.commodity.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.goods.domain.commodity.SpuId
import java.time.Instant

@DomainEventType(name = "commodity.off-sale", version = 1)
data class CommodityOffSaleEvent(
    val spuId: SpuId,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "commodity.off-sale"
    override val eventVersion = 1
    override val aggregateType = "Commodity"
    override val aggregateId = spuId.value.toString()
}
