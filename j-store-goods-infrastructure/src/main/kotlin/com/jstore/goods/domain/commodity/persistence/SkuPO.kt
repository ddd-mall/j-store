package com.jstore.goods.domain.commodity.persistence

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "sku")
class SkuPO(
    @Id
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "spu_id", nullable = false, insertable = false, updatable = false)
    var spuId: Long = 0,

    @Column(name = "sku_name", nullable = false, length = 256)
    var skuName: String = "",

    /** 销售属性 JSON，如 [{"key":"颜色","value":"红色"},{"key":"尺码","value":"XL"}] */
    @Column(name = "attributes", columnDefinition = "jsonb")
    var attributes: String = "[]",

    @Column(name = "price", nullable = false, precision = 19, scale = 0)
    var price: BigDecimal = BigDecimal.ZERO,
)
