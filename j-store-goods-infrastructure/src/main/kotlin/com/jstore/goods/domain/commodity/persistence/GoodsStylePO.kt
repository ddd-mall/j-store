package com.jstore.goods.domain.commodity.persistence

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "goods_style")
class GoodsStylePO(
    @Id
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "spu_id", nullable = false)
    var spuId: Long = 0,

    @Column(name = "main_images", columnDefinition = "jsonb", nullable = false)
    var mainImages: String = "[]",

    @Column(name = "detail_html", columnDefinition = "text", nullable = false)
    var detailHtml: String = "",

    @Column(name = "sku_images", columnDefinition = "jsonb", nullable = false)
    var skuImages: String = "{}",

    @Column(name = "create_time", nullable = false)
    var createTime: LocalDateTime = LocalDateTime.now(),

    @Column(name = "update_time", nullable = false)
    var updateTime: LocalDateTime = LocalDateTime.now(),
)
