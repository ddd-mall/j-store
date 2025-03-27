package com.jstore.goods.domain.commodity

import com.jstore.common.framework.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.goods.domain.commodity.comand.SKUAppendCMD
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import org.springframework.stereotype.Component


interface SpuFactory {
    fun create(createCmd: CommodityCreateCmd): Spu

    fun update(createCmd: CommodityCreateCmd, old: Spu) : Spu

    fun createSKU(skuAppendCMD: SKUAppendCMD) : List<Sku>
}

@Component
class SpuFactoryImpl(
    private val snowFlakSequence: SnowFlakSequence,
    private val domainEventPublisher: DomainEventPublisher,
) : SpuFactory {
    override fun create(createCmd: CommodityCreateCmd): Spu {
        return SpuImpl(
            id = SpuId(snowFlakSequence.nextId()),
            domainEventPublisher = domainEventPublisher,
            status = CommodityStatus.DRAFT,
            skus = ArrayList()
        )
    }

    override fun update(createCmd: CommodityCreateCmd, old: Spu): Spu {
        val spuImpl = SpuImpl(
            id = old.id,
            status = CommodityStatus.DRAFT,
            domainEventPublisher = domainEventPublisher,
            skus = ArrayList(),
        )
        if (old is SpuImpl) {
            spuImpl.status = old.status
        }
        return spuImpl

    }

    override fun createSKU(skuAppendCMD: SKUAppendCMD): List<Sku> {
        TODO("Not yet implemented")
    }
}