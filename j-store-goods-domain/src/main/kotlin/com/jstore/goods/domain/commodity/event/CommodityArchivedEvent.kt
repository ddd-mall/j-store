package com.jstore.goods.domain.commodity.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.goods.domain.commodity.SpuId
import java.time.Instant

@DomainEventType(name = "catalog.product-archived", version = 1)
data class CommodityArchivedEvent(
    val spuId: SpuId,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "catalog.product-archived"
    override val eventVersion = 1
    override val aggregateType = "CatalogProduct"
    override val aggregateId = spuId.value.toString()
}
