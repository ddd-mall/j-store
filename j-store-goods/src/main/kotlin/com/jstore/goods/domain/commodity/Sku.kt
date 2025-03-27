package com.jstore.goods.domain.commodity

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.goods.domain.inventory.CommodityCode

class SkuId(override val value: Long) : Id<Long>(value)

interface Sku : Entity<SkuId>

class SkuImpl(
    override val id: SkuId,
    val commodityCode: CommodityCode,
    val attributes: List<Attribute<String, String>>,
) : Sku