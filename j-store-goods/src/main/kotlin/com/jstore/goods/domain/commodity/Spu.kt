package com.jstore.goods.domain.commodity


import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id


class SpuId(override val value: Long) : Id<Long>(value)

interface Spu : Entity<SpuId> {
    /**
     * 开始售卖
     */
    fun putOnSale()

    /**
     * 停止售卖
     */
    fun tackOffSale()
}

class SpuImpl(
    override val id: SpuId,
    val  spuName: String,
) : Spu {
    override fun putOnSale() {
        TODO("Not yet implemented")
    }

    override fun tackOffSale() {
        TODO("Not yet implemented")
    }
}






