package com.jstore.goods.domain.commodity

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price

class SkuId(override val value: Long) : Id<Long>(value)

interface Sku : Entity<SkuId> {
    /** SKU 名称（如 "红色 / XL"） */
    val skuName: String

    /** 销售属性列表 */
    val attributes: List<Attribute<String, String>>

    /** SKU 单价 */
    val price: Price
}

class SkuImpl(
    override val id: SkuId,
    override val skuName: String,
    override val attributes: List<Attribute<String, String>>,
    override val price: Price,
) : Sku