package com.jstore.goods.domain.commodity

import com.jstore.goods.domain.commodity.comand.SKUAppendCMD
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import org.springframework.stereotype.Component


interface SpuFactory {
    fun create(createCmd: CommodityCreateCmd): Spu

    fun update(createCmd: CommodityCreateCmd, old: Spu) : Spu

    fun createSKU(skuAppendCMD: SKUAppendCMD) : List<Sku>
}

@Component
class SpuFactoryImpl : SpuFactory {
    override fun create(createCmd: CommodityCreateCmd): Spu {
        TODO("Not yet implemented")
    }

    override fun update(createCmd: CommodityCreateCmd, old: Spu): Spu {
        TODO("Not yet implemented")
    }

    override fun createSKU(skuAppendCMD: SKUAppendCMD): List<Sku> {
        TODO("Not yet implemented")
    }

}