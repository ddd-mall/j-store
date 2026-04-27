package com.jstore.goods.domain.commodity.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.goods.domain.commodity.SpuId

class CommodityOnSaleEvent(
    override val source: Any,
    val spuId: SpuId,
    /** 上架时的快照版本号 */
    val snapshotVersion: Long,
) : DomainEvent