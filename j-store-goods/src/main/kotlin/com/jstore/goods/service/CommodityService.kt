package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.utils.*
import com.jstore.goods.domain.commodity.*
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.comand.GoodsStyleSaveCmd
import com.jstore.goods.domain.commodity.comand.SkuCreateCmd
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshot
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactory
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotRepository

class CommodityService(
    private val spuFactory: SpuFactory,
    private val spuRepository: SpuRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val snapshotFactory: SpuSnapshotFactory,
    private val snapshotRepository: SpuSnapshotRepository,
    private val goodsStyleRepository: GoodsStyleRepository,
    private val goodsStyleFactory: GoodsStyleFactory,
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
     * 向SPU中添加SKU
     */
    fun addSku(cmd: SkuCreateCmd): Result<Spu, BusinessError> {
        val spu = spuRepository.findById(cmd.spuId) ?: return Failure(CommonBusinessError.OBJECT_NOT_FOUNT)
        val sku = spuFactory.createSku(cmd)
        spu.addSku(sku).onFailure { return Failure(it) }
        return Success(spuRepository.save(spu))
    }

    /**
     * 发布商品: DRAFT → OFF_SALE
     */
    fun publish(spuId: SpuId): Result<Unit, BusinessError> {
        val spu = spuRepository.findById(spuId) ?: return Failure(CommonBusinessError.OBJECT_NOT_FOUNT)
        spu.publish().onFailure { return Failure(it) }
        spuRepository.save(spu)
        spu.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }

    /**
     * 上架商品: OFF_SALE → ON_SALE，同时创建快照
     */
    fun putOnSale(spuId: SpuId): Result<SpuSnapshot, BusinessError> {
        val spu = spuRepository.findById(spuId) ?: return Failure(CommonBusinessError.OBJECT_NOT_FOUNT)
        spu.putOnSale().onFailure { return Failure(it) }
        // 递增版本并创建快照
        spu.incrementVersion()
        val snapshot = snapshotFactory.createSnapshot(spu)
        spuRepository.save(spu)
        snapshotRepository.save(snapshot)
        spu.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(snapshot)
    }

    /**
     * 下架商品: ON_SALE → OFF_SALE
     */
    fun takeOffSale(spuId: SpuId): Result<Unit, BusinessError> {
        val spu = spuRepository.findById(spuId) ?: return Failure(CommonBusinessError.OBJECT_NOT_FOUNT)
        spu.takeOffSale().onFailure { return Failure(it) }
        spuRepository.save(spu)
        spu.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }

    /**
     * 查询商品快照（供订单模块 ACL 调用）
     */
    fun querySnapshot(spuId: SpuId, version: Long): SpuSnapshot? {
        return snapshotRepository.findBySpuIdAndVersion(spuId, version)
    }

    /**
     * 查询商品最新快照
     */
    fun queryLatestSnapshot(spuId: SpuId): SpuSnapshot? {
        return snapshotRepository.findLatestBySpuId(spuId)
    }

    /**
     * 保存或更新商品展示样式
     */
    fun saveGoodsStyle(cmd: GoodsStyleSaveCmd): Result<GoodsStyle, BusinessError> {
        cmd.verify().onFailure { return Failure(it) }

        spuRepository.findById(cmd.spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)

        val existing = goodsStyleRepository.findBySpuId(cmd.spuId)
        val goodsStyle = if (existing != null) {
            existing.updateMainImages(cmd.mainImages).onFailure { return Failure(it) }
            existing.updateDetailHtml(cmd.detailHtml).onFailure { return Failure(it) }
            for ((skuId, images) in cmd.skuImages) {
                existing.updateSkuImages(skuId, images).onFailure { return Failure(it) }
            }
            existing
        } else {
            goodsStyleFactory.create(cmd.spuId, cmd.mainImages, cmd.detailHtml, cmd.skuImages)
        }

        return Success(goodsStyleRepository.save(goodsStyle))
    }
}
