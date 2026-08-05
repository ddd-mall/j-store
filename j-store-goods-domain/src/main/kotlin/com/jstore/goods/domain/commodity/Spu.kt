package com.jstore.goods.domain.commodity

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.framework.RecordsDomainEvents
import com.jstore.common.properties.Id
import com.jstore.common.utils.Result

data class MerchantId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0) { "merchantId must be positive" }
    }
}

/** TODO: 商品的 Copy-on-Write 流程应该适用于所有状态 */
interface Spu : AggregateRoot<SpuId>, RecordsDomainEvents {
    /** 商品所属商户。一个商品的全部 SKU 共享同一商户归属。 */
    val merchantId: MerchantId

    /** 商品名称 */
    val name: String

    /** 商品描述 */
    val description: String

    /** SKU 列表（只读视图） */
    val skus: List<Sku>

    /** 商品状态 */
    val status: CommodityStatus

    /** 版本号（每次快照递增） */
    val version: Long

    /** 源商品 ID：null 表示原始商品，非 null 表示该 SPU 是指定源商品的草稿副本 */
    val sourceSpuId: SpuId?

    /** 添加 SKU */
    fun addSku(sku: Sku): Result<Unit, BusinessError>

    /** 发布商品资料：DRAFT → PUBLISHED。 */
    fun publish(): Result<Unit, BusinessError>

    /** 归档商品资料：PUBLISHED → ARCHIVED。 */
    fun archive(): Result<Unit, BusinessError>

    /** 将草稿副本的内容合并到当前 SPU（领域方法） */
    fun mergeFromDraft(draft: Spu): Result<Unit, BusinessError>

}
