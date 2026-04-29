package com.jstore.goods.domain.commodity.persistence

import com.jstore.goods.domain.commodity.CommodityStatus
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "spu")
class SpuPO(
    @Id
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "name", nullable = false, length = 256)
    var name: String = "",

    @Column(name = "description", length = 2000)
    var description: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: CommodityStatus = CommodityStatus.DRAFT,

    @Column(name = "version", nullable = false)
    var version: Long = 1,

    @Column(name = "source_spu_id")
    var sourceSpuId: Long? = null,

    @Column(name = "create_time", nullable = false)
    var createTime: LocalDateTime = LocalDateTime.now(),

    @Column(name = "update_time", nullable = false)
    var updateTime: LocalDateTime = LocalDateTime.now(),

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "spu_id")
    var skus: MutableList<SkuPO> = mutableListOf(),
)
