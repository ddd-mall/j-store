package com.jstore.goods.domain.commodity


import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.utils.Result


class SpuId(override val value: Long) : Id<Long>(value)

interface Spu : Entity<SpuId> {
    /**
     * 增加SKU
     */
    fun addSku(sku: Sku): Result<Boolean, BusinessError>

    /**
     * 开始售卖
     */
    fun putOnSale(): Result<Boolean, BusinessError>

    /**
     * 停止售卖
     */
    fun tackOffSale(): Result<Boolean, BusinessError>

    /**
     * 发布
     */
    fun publish(): Result<Boolean, BusinessError>
}







