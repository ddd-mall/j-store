package com.jstore.goods.domain.commodity

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AgreeGate
import com.jstore.common.properties.Price
import com.jstore.common.utils.Result


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

    /** 添加 SKU */
    fun addSku(sku: Sku): Result<Unit, BusinessError>

    /** 发布：DRAFT → OFF_SALE */
    fun publish(): Result<Unit, BusinessError>

    /** 上架：OFF_SALE → ON_SALE */
    fun putOnSale(): Result<Unit, BusinessError>

    /** 下架：ON_SALE → OFF_SALE */
    fun takeOffSale(): Result<Unit, BusinessError>

    /** 递增版本号（用于快照前） */
    fun incrementVersion(): Long
}
