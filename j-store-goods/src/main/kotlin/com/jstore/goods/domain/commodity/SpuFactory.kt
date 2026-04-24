package com.jstore.goods.domain.commodity

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.comand.SKUAppendCMD


interface SpuFactory {
    fun create(createCmd: CommodityCreateCmd): Spu

    fun update(createCmd: CommodityCreateCmd, old: Spu): Spu

    fun createSKU(skuAppendCMD: SKUAppendCMD): List<Sku>
}

class SpuFactoryImpl(
    private val snowFlakSequence: SnowFlakSequence,
) : SpuFactory {
    override fun create(createCmd: CommodityCreateCmd): Spu {
        return SpuImpl(
            id = SpuId(snowFlakSequence.nextId()),
            name = createCmd.spuName,
            _status = CommodityStatus.DRAFT,
            _skus = ArrayList(),
        )
    }

    override fun update(createCmd: CommodityCreateCmd, old: Spu): Spu {
        return SpuImpl(
            id = old.id,
            name = createCmd.spuName,
            _status = old.status,
            _skus = old.skus.toMutableList(),
        )
    }

    override fun createSKU(skuAppendCMD: SKUAppendCMD): List<Sku> {
        TODO("Not yet implemented")
    }
}
