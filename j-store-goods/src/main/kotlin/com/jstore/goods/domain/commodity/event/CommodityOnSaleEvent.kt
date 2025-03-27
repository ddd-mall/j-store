package com.jstore.goods.domain.commodity.event

import com.jstore.common.framework.DomainEvent
import com.jstore.goods.domain.commodity.SpuId

class CommodityOnSaleEvent(
    source: Any,
    val spuId: SpuId,
) : DomainEvent(source)