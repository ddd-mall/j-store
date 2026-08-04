package com.jstore.goods.domain.commodity.snapshot

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.goods.domain.commodity.*
import java.time.LocalDateTime

/**
 * SPU 快照 ID
 */
class SpuSnapshotId(override val value: Long) : Id<Long>(value)

/**
 * SPU 快照 — 不可变值对象，记录某一时刻的商品完整信息。
 * 订单创建时引用快照版本，确保历史价格和属性可追溯。
 */
data class SpuSnapshot(
    override val id: SpuSnapshotId,
    /** 商品所属商户 */
    val merchantId: MerchantId,
    /** 原始 SPU ID */
    val spuId: SpuId,
    /** 快照版本号（与 SPU.version 对应） */
    val snapshotVersion: Long,
    /** 商品名称 */
    val spuName: String,
    /** 商品描述 */
    val description: String,
    /** SKU 快照列表 */
    val skuSnapshots: List<SkuSnapshot>,
    /** 快照创建时间 */
    val createdAt: LocalDateTime,
) : Entity<SpuSnapshotId>

/**
 * SKU 快照 — 不可变值对象
 */
data class SkuSnapshot(
    val skuId: SkuId,
    val skuName: String,
    val attributes: List<Attribute<String, String>>,
    val price: Price,
    val merchantCode: String? = null,
    val barcode: String? = null,
)
