/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.utils.*
import com.jstore.goods.api.GoodsSkuSnapshotInfo
import com.jstore.goods.api.GoodsSnapshotInfo
import com.jstore.goods.api.GoodsSnapshotQueryService
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
) : GoodsSnapshotQueryService {

    /**
     * 创建/更新SPU（拦截 ON_SALE 商品的直接编辑）
     *
     * TODO: 与 editOnSale 可能存在职能上的重复
     */
    fun createOrUpdate(cmd: CommodityCreateCmd): Result<Spu, BusinessError> {
        return cmd.verify().map {
            cmd.spuId?.let {
                val old =
                    spuRepository.findById(it) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
                // 拦截 ON_SALE 商品直接编辑
                if (old.status == CommodityStatus.ON_SALE) {
                    return Failure(CommodityErrors.ON_SALE_DIRECT_EDIT_REJECTED)
                }
                val update = spuFactory.update(cmd, old)
                return@map spuRepository.save(update)
            }
            val spu = spuFactory.create(cmd)
            spuRepository.save(spu)
        }
    }

    /**
     * 向SPU中添加SKU
     *
     * TODO: 缺失删除SKU的操作
     */
    fun addSku(cmd: SkuCreateCmd): Result<Spu, BusinessError> {
        val spu = spuRepository.findById(cmd.spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        val sku = spuFactory.createSku(cmd)
        spu.addSku(sku).onFailure {
            return Failure(it)
        }
        return Success(spuRepository.save(spu))
    }

    /**
     * 发布商品: DRAFT → OFF_SALE
     *
     * TODO: 如果此对象是另一个SPU的草稿副本，不应该允许发布，应该先合并回源商品后由源商品发布
     */
    fun publish(spuId: SpuId): Result<Unit, BusinessError> {
        val spu = spuRepository.findById(spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        spu.publish().onFailure {
            return Failure(it)
        }
        spuRepository.save(spu)
        spu.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }

    /**
     * 上架商品: OFF_SALE → ON_SALE，同时创建快照
     *
     * TODO: 同样的，如果SPU本身是另一个SPU的草稿副本，不应该被允许上架
     */
    fun putOnSale(spuId: SpuId): Result<SpuSnapshot, BusinessError> {
        val spu = spuRepository.findById(spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        spu.putOnSale().onFailure {
            return Failure(it)
        }
        val snapshot = snapshotFactory.createSnapshot(spu)
        spuRepository.save(spu)
        snapshotRepository.save(snapshot)
        spu.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(snapshot)
    }

    /** 下架商品: ON_SALE → OFF_SALE */
    fun takeOffSale(spuId: SpuId): Result<Unit, BusinessError> {
        val spu = spuRepository.findById(spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        spu.takeOffSale().onFailure {
            return Failure(it)
        }
        spuRepository.save(spu)
        spu.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(Unit)
    }

    override fun queryLatestSnapshots(spuIds: List<Long>): List<GoodsSnapshotInfo> {
        return spuIds.distinct().mapNotNull { spuId ->
            snapshotRepository.findLatestBySpuId(SpuId(spuId))?.let { snapshot ->
                GoodsSnapshotInfo(
                    spuId = snapshot.spuId.value,
                    merchantId = snapshot.merchantId.value,
                    snapshotVersion = snapshot.snapshotVersion,
                    spuName = snapshot.spuName,
                    skuSnapshots =
                        snapshot.skuSnapshots.map { skuSnapshot ->
                            GoodsSkuSnapshotInfo(
                                skuId = skuSnapshot.skuId.value,
                                skuName = skuSnapshot.skuName,
                                attributes = skuSnapshot.attributes.map { it.key to it.value },
                                price = skuSnapshot.price,
                            )
                        },
                )
            }
        }
    }

    /**
     * 获取在售商品的可编辑草稿副本
     * - 已有草稿 → 直接返回（幂等）
     * - 无草稿 → 创建并持久化后返回
     */
    fun getDraft(spuId: SpuId): Result<Spu, BusinessError> {
        val spu = spuRepository.findById(spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        if (spu.status != CommodityStatus.ON_SALE) {
            return Failure(CommodityErrors.ONLY_ON_SALE_NEEDS_DRAFT)
        }
        // 幂等：已有草稿直接返回
        val existingDraft = spuRepository.findDraftBySourceSpuId(spuId)
        if (existingDraft != null) {
            return Success(existingDraft)
        }
        // 创建草稿副本
        val draft =
            spuFactory.createDraftCopy(spu).getOrElse {
                return Failure(it)
            }
        return Success(spuRepository.save(draft))
    }

    /** 发布草稿 — 合并回源商品、递增版本、生成快照、删除草稿 */
    fun publishDraft(draftSpuId: SpuId): Result<SpuSnapshot, BusinessError> {
        val draft =
            spuRepository.findById(draftSpuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        if (draft.sourceSpuId == null) {
            return Failure(CommodityErrors.NOT_A_DRAFT_COPY)
        }
        val source =
            spuRepository.findById(draft.sourceSpuId!!)
                ?: return Failure(CommodityErrors.SPU_NOT_FOUND)

        // 领域方法：合并草稿内容到源商品
        source.mergeFromDraft(draft).onFailure {
            return Failure(it)
        }

        // 创建新快照
        val snapshot = snapshotFactory.createSnapshot(source)

        // 持久化
        spuRepository.save(source)
        snapshotRepository.save(snapshot)
        spuRepository.delete(draft)

        source.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }
        return Success(snapshot)
    }

    /** 丢弃草稿 — 删除草稿副本，源商品不受影响 */
    fun discardDraft(draftSpuId: SpuId): Result<Unit, BusinessError> {
        val draft =
            spuRepository.findById(draftSpuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        if (draft.sourceSpuId == null) {
            return Failure(CommodityErrors.NOT_A_DRAFT_COPY)
        }
        spuRepository.delete(draft)
        return Success(Unit)
    }

    /** 保存或更新商品展示样式 */
    fun saveGoodsStyle(cmd: GoodsStyleSaveCmd): Result<GoodsStyle, BusinessError> {
        cmd.verify().onFailure {
            return Failure(it)
        }

        spuRepository.findById(cmd.spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)

        val existing = goodsStyleRepository.findBySpuId(cmd.spuId)
        val goodsStyle =
            if (existing != null) {
                existing.updateMainImages(cmd.mainImages).onFailure {
                    return Failure(it)
                }
                existing.updateDetailHtml(cmd.detailHtml).onFailure {
                    return Failure(it)
                }
                for ((skuId, images) in cmd.skuImages) {
                    existing.updateSkuImages(skuId, images).onFailure {
                        return Failure(it)
                    }
                }
                existing
            } else {
                goodsStyleFactory.create(cmd.spuId, cmd.mainImages, cmd.detailHtml, cmd.skuImages)
            }

        return Success(goodsStyleRepository.save(goodsStyle))
    }
}
