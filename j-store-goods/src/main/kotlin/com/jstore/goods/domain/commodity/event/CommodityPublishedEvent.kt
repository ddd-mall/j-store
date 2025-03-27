package com.jstore.goods.domain.commodity.event

import com.jstore.common.framework.DomainEvent
import com.jstore.goods.domain.commodity.SpuId

class CommodityPublishedEvent(
    source: Any,
    val spuId: SpuId
): DomainEvent(source)