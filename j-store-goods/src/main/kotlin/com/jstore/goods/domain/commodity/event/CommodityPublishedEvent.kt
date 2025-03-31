package com.jstore.goods.domain.commodity.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.goods.domain.commodity.SpuId

class CommodityPublishedEvent(
    override val source: Any,
    val spuId: SpuId
): DomainEvent