package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.framework.DomainEventPublisher
import com.jstore.common.utils.*
import com.jstore.goods.domain.commodity.*
import com.jstore.goods.domain.commodity.comand.SKUAppendCMD
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.event.CommodityOffSaleEvent
import com.jstore.goods.domain.commodity.event.CommodityOnSaleEvent
import com.jstore.goods.domain.commodity.event.CommodityPublishedEvent
import org.springframework.stereotype.Service

@Service
class CommodityService(
    private val spuFactory: SpuFactory,
    private val spuRepository: SpuRepository,
    private val domainEventPublisher: DomainEventPublisher,
) {

    /**
     * 创建/更新SPU
     */
    fun createOrUpdate(cmd: CommodityCreateCmd): Result<Spu, BusinessError> {
        return cmd.verify()
            .map {
                cmd.spuId?.let {
                    val old = spuRepository.findById(it) ?: return Failure(CommonBusinessError.OBJECT_NOT_FOUNT)
                    val update = spuFactory.update(cmd, old)
                    return@map spuRepository.save(update)
                }
                val spu = spuFactory.create(cmd)
                spuRepository.save(spu)
            }
    }


    /**
     * 向SPU中追加SKU
     */
    fun appendSku(skuAppendCMD: SKUAppendCMD): Result<Spu, BusinessError> {
        val spu = spuRepository.findById(skuAppendCMD.spuId) ?: return Failure(CommonBusinessError.OBJECT_NOT_FOUNT)
        return skuAppendCMD.verify()
            .map { cmd ->
                spuFactory.createSKU(cmd).forEach {
                    spu.addSku(it).onFailure { e -> return Failure(e) }
                }
                spuRepository.save(spu)
            }
    }

    /**
     * 发布商品
     */
    fun publish(spuId: SpuId): Result<Boolean, BusinessError> {
        val spu = spuRepository.findById(spuId) ?: return Failure(CommonBusinessError.OBJECT_NOT_FOUNT)
        spu.publish().onFailure { e -> return Failure(e) }
        spuRepository.save(spu)
        domainEventPublisher.publishEvent(CommodityPublishedEvent(spu, spu.id))
        return Success(true)
    }

    /**
     * 上架商品
     */
    fun putOnSale(spuId: SpuId): Result<Boolean, BusinessError> {
        val spu = spuRepository.findById(spuId) ?: return Failure(CommonBusinessError.OBJECT_NOT_FOUNT)
        spu.putOnSale().onFailure { e -> return Failure(e) }
        spuRepository.save(spu)
        domainEventPublisher.publishEvent(CommodityOnSaleEvent(spu, spuId))
        return Success(true)
    }

    /**
     * 下架商品
     */
    fun takeOffSale(spuId: SpuId): Result<Boolean, BusinessError> {
        val spu = spuRepository.findById(spuId) ?: return Failure(CommonBusinessError.OBJECT_NOT_FOUNT)
        spu.tackOffSale().onFailure { e -> return Failure(e) }
        spuRepository.save(spu)
        domainEventPublisher.publishEvent(CommodityOffSaleEvent(spu, spuId))
        return Success(true)
    }


}