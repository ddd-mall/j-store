package com.jstore.goods.domain.commodity.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import com.jstore.goods.domain.commodity.SpuId
import java.time.Instant

@DomainEventType(name = "commodity.off-sale", version = 1)
class CommodityOffSaleEvent(
    override val source: Any,
    val spuId: SpuId,
    override val occurredAt: Instant = Instant.now(),
) : ExplicitDomainEvent {
    override val eventName: String = "commodity.off-sale"
    override val eventVersion: Int = 1
    override val aggregateType: String = "Commodity"
    override val aggregateId: String = spuId.value.toString()
    override val eventId: String =
        stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}
