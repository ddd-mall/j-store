package com.jstore.goods.domain.commodity.persistence

import jakarta.persistence.*

@Entity
@Table(name = "sku")
class SkuPO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "spu_id", nullable = false, insertable = false, updatable = false)
    var spuId: Long = 0,
    @Column(name = "sku_name", nullable = false, length = 256) var skuName: String = "",

    /** 销售属性 JSON，如 [{"key":"颜色","value":"红色"},{"key":"尺码","value":"XL"}] */
    @Column(name = "attributes", columnDefinition = "jsonb") var attributes: String = "[]",
    @Column(name = "merchant_code", length = 128) var merchantCode: String? = null,
    @Column(name = "barcode", length = 64) var barcode: String? = null,
)
