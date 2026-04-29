package com.jstore.goods.domain.commodity

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AgreeGate
import com.jstore.common.utils.Result


/**
 * TODO: 商品的 Copy-on-Write 流程应该适用于所有状态
 */
interface Spu : AgreeGate<SpuId> {
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

    /** 发布：DRAFT → OFF_SALE */
    fun publish(): Result<Unit, BusinessError>

    /** 上架：OFF_SALE → ON_SALE */
    fun putOnSale(): Result<Unit, BusinessError>

    /** 下架：ON_SALE → OFF_SALE */
    fun takeOffSale(): Result<Unit, BusinessError>

    /** 将草稿副本的内容合并到当前 SPU（领域方法） */
    fun mergeFromDraft(draft: Spu): Result<Unit, BusinessError>
}
