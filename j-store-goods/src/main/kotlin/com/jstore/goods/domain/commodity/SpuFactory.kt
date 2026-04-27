package com.jstore.goods.domain.commodity

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.comand.SkuCreateCmd


interface SpuFactory {
    fun create(createCmd: CommodityCreateCmd): Spu
    fun update(createCmd: CommodityCreateCmd, old: Spu): Spu
    fun createSku(cmd: SkuCreateCmd): Sku
}

class SpuFactoryImpl(
    private val snowFlakSequence: SnowFlakSequence,
) : SpuFactory {

    override fun create(createCmd: CommodityCreateCmd): Spu {
        return SpuImpl(
            id = SpuId(snowFlakSequence.nextId()),
            name = createCmd.spuName,
            description = createCmd.description,
            _status = CommodityStatus.DRAFT,
            _skus = ArrayList(),
        )
    }

    override fun update(createCmd: CommodityCreateCmd, old: Spu): Spu {
        return SpuImpl(
            id = old.id,
            name = createCmd.spuName,
            description = createCmd.description,
            _status = old.status,
            _skus = old.skus.toMutableList(),
            _version = old.version,
        )
    }

    override fun createSku(cmd: SkuCreateCmd): Sku {
        return SkuImpl(
            id = SkuId(snowFlakSequence.nextId()),
            skuName = cmd.skuName,
            attributes = cmd.attributes,
            price = cmd.price,
        )
    }
}
