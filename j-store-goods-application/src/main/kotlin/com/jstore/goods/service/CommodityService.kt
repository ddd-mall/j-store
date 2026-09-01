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
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.utils.*
import com.jstore.goods.api.GoodsSkuSnapshotInfo
import com.jstore.goods.api.GoodsSnapshotInfo
import com.jstore.goods.api.GoodsSnapshotQueryService
import com.jstore.goods.api.CurrentGoodsSkuInfo
import com.jstore.goods.api.CurrentGoodsSkuQueryService
import com.jstore.goods.domain.brand.Brand
import com.jstore.goods.domain.brand.BrandErrors
import com.jstore.goods.domain.brand.BrandId
import com.jstore.goods.domain.brand.BrandRepository
import com.jstore.goods.domain.brand.BrandStatus
import com.jstore.goods.domain.commodity.*
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.comand.GoodsStyleSaveCmd
import com.jstore.goods.domain.commodity.comand.SkuCreateCmd
import com.jstore.goods.domain.commodity.comand.SkuRemoveCmd
import com.jstore.goods.domain.commodity.comand.SkuUpdateCmd
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshot
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactory
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotRepository
import com.jstore.goods.domain.producttype.ProductTypeErrors
import com.jstore.goods.domain.producttype.ProductTypeRepository

class CommodityService(
    private val spuFactory: SpuFactory,
    private val spuRepository: SpuRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val snapshotFactory: SpuSnapshotFactory,
    private val snapshotRepository: SpuSnapshotRepository,
    private val goodsStyleRepository: GoodsStyleRepository,
    private val goodsStyleFactory: GoodsStyleFactory,
    private val brandRepository: BrandRepository,
    private val productTypeRepository: ProductTypeRepository? = null,
) : CommodityUseCase, GoodsSnapshotQueryService, CurrentGoodsSkuQueryService {

    override fun querySkus(skuIds: List<Long>): List<CurrentGoodsSkuInfo> =
        spuRepository.findPublishedBySkuIds(skuIds.distinct().map(::SkuId)).flatMap { spu ->
            spu.skus.filter { it.id.value in skuIds }.map { sku ->
                CurrentGoodsSkuInfo(sku.id.value, spu.id.value, spu.merchantId.value, true, spu.version, spu.name, sku.skuName)
            }
        }

    /**
     * 创建/更新 SPU（已发布资料必须通过草稿副本修改）
     *
     * TODO: 与 editOnSale 可能存在职能上的重复
     */
    override fun createOrUpdate(cmd: CommodityCreateCmd): Result<Spu, BusinessError> {
        return cmd.verify().map {
            validateBrandReference(cmd.brandId, MerchantId(cmd.merchantId)).onFailure {
                return Failure(it)
            }
            cmd.spuId?.let {
                val old =
                    spuRepository.findById(it) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
                if (old.status == CommodityStatus.PUBLISHED) {
                    return Failure(CommodityErrors.PUBLISHED_DIRECT_EDIT_REJECTED)
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
    override fun addSku(cmd: SkuCreateCmd): Result<Spu, BusinessError> {
        val spu = spuRepository.findById(cmd.spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        val sku = spuFactory.createSku(cmd)
        spu.addSku(sku).onFailure {
            return Failure(it)
        }
        return Success(spuRepository.save(spu))
    }

    override fun updateSku(cmd: SkuUpdateCmd): Result<Spu, BusinessError> {
        val spu = spuRepository.findById(cmd.spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        spu.updateSku(spuFactory.createSku(cmd)).onFailure {
            return Failure(it)
        }
        return Success(spuRepository.save(spu))
    }

    override fun removeSku(cmd: SkuRemoveCmd): Result<Spu, BusinessError> {
        val spu = spuRepository.findById(cmd.spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        spu.removeSku(cmd.skuId).onFailure {
            return Failure(it)
        }
        return Success(spuRepository.save(spu))
    }

    /**
     * 发布商品资料并创建版本快照。
     *
     * TODO: 如果此对象是另一个SPU的草稿副本，不应该允许发布，应该先合并回源商品后由源商品发布
     */
    override fun publish(spuId: SpuId): Result<SpuSnapshot, BusinessError> {
        val spu = spuRepository.findById(spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        val brand =
            findValidBrand(spu.brandId, spu.merchantId).getOrElse {
                return Failure(it)
            }
        validateProductType(spu).onFailure {
            return Failure(it)
        }
        spu.publish().onFailure {
            return Failure(it)
        }
        val snapshot =
            snapshotFactory.createSnapshot(
                spu,
                goodsStyleRepository.findBySpuId(spu.id),
                brand?.name,
            )
        spuRepository.save(spu)
        snapshotRepository.save(snapshot)
        spu.publishPendingEvents(domainEventPublisher)
        return Success(snapshot)
    }

    override fun archive(spuId: SpuId): Result<Unit, BusinessError> {
        val spu = spuRepository.findById(spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        spu.archive().onFailure {
            return Failure(it)
        }
        spuRepository.save(spu)
        spu.publishPendingEvents(domainEventPublisher)
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
                    description = snapshot.description,
                    mainImages = snapshot.mainImages,
                    detailHtml = snapshot.detailHtml,
                    productTypeId = snapshot.productTypeId?.value,
                    productAttributes = snapshot.productAttributes.map { it.key to it.value },
                    brandId = snapshot.brandId?.value,
                    brandName = snapshot.brandName?.values.orEmpty(),
                    categoryIds = snapshot.categoryIds.map { it.value }.toSet(),
                    localizedNames = snapshot.localizedNames?.values.orEmpty(),
                    localizedDescriptions = snapshot.localizedDescriptions?.values.orEmpty(),
                    skuSnapshots =
                        snapshot.skuSnapshots.map { skuSnapshot ->
                            GoodsSkuSnapshotInfo(
                                skuId = skuSnapshot.skuId.value,
                                skuName = skuSnapshot.skuName,
                                attributes = skuSnapshot.attributes.map { it.key to it.value },
                                imageKeys = skuSnapshot.imageKeys,
                            )
                        },
                )
            }
        }
    }

    /**
     * 获取已发布商品资料的可编辑草稿副本
     * - 已有草稿 → 直接返回（幂等）
     * - 无草稿 → 创建并持久化后返回
     */
    override fun getDraft(spuId: SpuId): Result<Spu, BusinessError> {
        val spu = spuRepository.findById(spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        if (spu.status != CommodityStatus.PUBLISHED) {
            return Failure(CommodityErrors.ONLY_PUBLISHED_NEEDS_DRAFT)
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
        val savedDraft = spuRepository.save(draft)
        copyStyleToDraft(spu, savedDraft)
        return Success(savedDraft)
    }

    /** 发布草稿 — 合并回源商品、递增版本、生成快照、删除草稿 */
    override fun publishDraft(draftSpuId: SpuId): Result<SpuSnapshot, BusinessError> {
        val draft =
            spuRepository.findById(draftSpuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        if (draft.sourceSpuId == null) {
            return Failure(CommodityErrors.NOT_A_DRAFT_COPY)
        }
        val source =
            spuRepository.findById(draft.sourceSpuId!!)
                ?: return Failure(CommodityErrors.SPU_NOT_FOUND)

        val draftStyle = goodsStyleRepository.findBySpuId(draft.id)
        val stableSkuIds = draft.skus.associate { it.id to (it.sourceSkuId ?: it.id) }
        val brand =
            findValidBrand(draft.brandId, draft.merchantId).getOrElse {
                return Failure(it)
            }
        validateProductType(draft).onFailure {
            return Failure(it)
        }

        // 领域方法：合并草稿内容到源商品
        source.mergeFromDraft(draft).onFailure {
            return Failure(it)
        }

        val publishedStyle =
            publishDraftStyle(source, draftStyle, stableSkuIds).getOrElse {
                return Failure(it)
            }

        // 创建新快照
        val snapshot = snapshotFactory.createSnapshot(source, publishedStyle, brand?.name)

        // 持久化
        spuRepository.save(source)
        snapshotRepository.save(snapshot)
        if (draftStyle != null) goodsStyleRepository.delete(draftStyle)
        spuRepository.delete(draft)

        source.publishPendingEvents(domainEventPublisher)
        return Success(snapshot)
    }

    /** 丢弃草稿 — 删除草稿副本，源商品不受影响 */
    override fun discardDraft(draftSpuId: SpuId): Result<Unit, BusinessError> {
        val draft =
            spuRepository.findById(draftSpuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        if (draft.sourceSpuId == null) {
            return Failure(CommodityErrors.NOT_A_DRAFT_COPY)
        }
        goodsStyleRepository.findBySpuId(draft.id)?.let { goodsStyleRepository.delete(it) }
        spuRepository.delete(draft)
        return Success(Unit)
    }

    /** 保存或更新商品展示样式 */
    override fun saveGoodsStyle(cmd: GoodsStyleSaveCmd): Result<GoodsStyle, BusinessError> {
        cmd.verify().onFailure {
            return Failure(it)
        }

        val spu = spuRepository.findById(cmd.spuId) ?: return Failure(CommodityErrors.SPU_NOT_FOUND)
        if (spu.status != CommodityStatus.DRAFT) {
            return Failure(CommodityErrors.PUBLISHED_DIRECT_EDIT_REJECTED)
        }
        if (cmd.skuImages.keys.any { skuId -> spu.skus.none { it.id == skuId } }) {
            return Failure(CommodityErrors.SKU_NOT_FOUND)
        }

        val existing = goodsStyleRepository.findBySpuId(cmd.spuId)
        val goodsStyle =
            if (existing != null) {
                existing.replaceContent(cmd.mainImages, cmd.detailHtml, cmd.skuImages).onFailure {
                    return Failure(it)
                }
                existing
            } else {
                goodsStyleFactory.create(cmd.spuId, cmd.mainImages, cmd.detailHtml, cmd.skuImages)
            }

        return Success(goodsStyleRepository.save(goodsStyle))
    }

    private fun copyStyleToDraft(source: Spu, draft: Spu) {
        val sourceStyle = goodsStyleRepository.findBySpuId(source.id) ?: return
        val draftSkuIdsBySource =
            draft.skus.mapNotNull { sku -> sku.sourceSkuId?.let { it to sku.id } }.toMap()
        val draftStyle =
            goodsStyleFactory.create(
                draft.id,
                sourceStyle.mainImages,
                sourceStyle.detailHtml,
                sourceStyle.skuImages
                    .mapNotNull { (sourceSkuId, images) ->
                        draftSkuIdsBySource[sourceSkuId]?.let { it to images }
                    }
                    .toMap(),
            )
        goodsStyleRepository.save(draftStyle)
    }

    private fun publishDraftStyle(
        source: Spu,
        draftStyle: GoodsStyle?,
        stableSkuIds: Map<SkuId, SkuId>,
    ): Result<GoodsStyle?, BusinessError> {
        if (draftStyle == null) return Success(goodsStyleRepository.findBySpuId(source.id))
        val stableSkuImages =
            draftStyle.skuImages
                .mapNotNull { (draftSkuId, images) ->
                    stableSkuIds[draftSkuId]?.let { it to images }
                }
                .toMap()
        val sourceStyle =
            goodsStyleRepository.findBySpuId(source.id)
                ?: goodsStyleFactory.create(
                    source.id,
                    draftStyle.mainImages,
                    draftStyle.detailHtml,
                    stableSkuImages,
                )
        sourceStyle
            .replaceContent(draftStyle.mainImages, draftStyle.detailHtml, stableSkuImages)
            .onFailure {
                return Failure(it)
            }
        return Success(goodsStyleRepository.save(sourceStyle))
    }

    private fun validateProductType(spu: Spu): Result<Unit, BusinessError> {
        val productTypeId = spu.productTypeId ?: return Success(Unit)
        val productType =
            productTypeRepository?.findById(productTypeId)
                ?: return Failure(ProductTypeErrors.NOT_FOUND)
        if (productType.merchantId != spu.merchantId) {
            return Failure(ProductTypeErrors.MERCHANT_MISMATCH)
        }
        return productType.validate(spu.productAttributes, spu.skus)
    }

    private fun validateBrandReference(
        brandId: BrandId?,
        merchantId: MerchantId,
    ): Result<Unit, BusinessError> {
        findValidBrand(brandId, merchantId).onFailure {
            return Failure(it)
        }
        return Success(Unit)
    }

    private fun findValidBrand(
        brandId: BrandId?,
        merchantId: MerchantId,
    ): Result<Brand?, BusinessError> {
        brandId ?: return Success(null)
        val brand = brandRepository.findById(brandId) ?: return Failure(BrandErrors.NOT_FOUND)
        if (brand.merchantId != merchantId) {
            return Failure(BrandErrors.MERCHANT_MISMATCH)
        }
        if (brand.status != BrandStatus.ACTIVE) {
            return Failure(BrandErrors.INACTIVE)
        }
        return Success(brand)
    }
}
