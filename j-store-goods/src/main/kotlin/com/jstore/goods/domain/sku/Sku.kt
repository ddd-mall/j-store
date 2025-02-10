package com.jstore.goods.domain.sku

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price

class SkuId(override val value: Long) : Id<Long>(value)
class Sku(
    private val id: SkuId,
    val name: String,
    val unitPrice: Price,
) : Entity<SkuId> {
    override fun id(): SkuId {
        return id
    }
}