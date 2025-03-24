package com.jstore.goods.domain.spu

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price

class SkuId(override val value: Long) : Id<Long>(value)

class Sku(
    override val id: SkuId,
    val name: String,
    val unitPrice: Price,
) : Entity<SkuId>