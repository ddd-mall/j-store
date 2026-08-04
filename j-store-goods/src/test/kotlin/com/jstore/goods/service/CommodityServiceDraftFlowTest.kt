package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.*
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshot
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactory
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotId
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.mockito.kotlin.*
import java.time.LocalDateTime

class CommodityServiceDraftFlowTest : FunSpec({

    lateinit var spuFactory: SpuFactory
    lateinit var spuRepository: SpuRepository
    lateinit var domainEventPublisher: DomainEventPublisher
    lateinit var snapshotFactory: SpuSnapshotFactory
    lateinit var snapshotRepository: SpuSnapshotRepository
    lateinit var goodsStyleRepository: GoodsStyleRepository
    lateinit var goodsStyleFactory: GoodsStyleFactory
    lateinit var service: CommodityService

    beforeEach {
        spuFactory = mock()
        spuRepository = mock()
        domainEventPublisher = mock()
        snapshotFactory = mock()
        snapshotRepository = mock()
        goodsStyleRepository = mock()
        goodsStyleFactory = mock()
        service = CommodityService(
            spuFactory = spuFactory,
            spuRepository = spuRepository,
            domainEventPublisher = domainEventPublisher,
            snapshotFactory = snapshotFactory,
            snapshotRepository = snapshotRepository,
            goodsStyleRepository = goodsStyleRepository,
            goodsStyleFactory = goodsStyleFactory,
        )
    }

    val sourceSpuId = SpuId(100L)
    val draftSpuId = SpuId(200L)

    fun createSku(id: Long, name: String = "sku-$id"): Sku = SkuImpl(
        id = SkuId(id),
        skuName = name,
        attributes = listOf(Attribute("color", "red")),
        price = Price.ofFen(9900L),
    )

    fun createOnSaleSpu(
        id: SpuId = sourceSpuId,
        name: String = "在售商品",
        version: Long = 1L,
    ): SpuImpl = SpuImpl(
        id = id,
        name = name,
        description = "商品描述",
        _status = CommodityStatus.ON_SALE,
        _skus = mutableListOf(createSku(1L)),
        _version = version,
    )

    fun createDraftSpu(
        id: SpuId = draftSpuId,
        sourceId: SpuId = sourceSpuId,
        name: String = "草稿商品",
    ): SpuImpl = SpuImpl(
        id = id,
        name = name,
        description = "草稿描述",
        _status = CommodityStatus.DRAFT,
        _skus = mutableListOf(createSku(2L, "draft-sku")),
        _version = 1L,
        sourceSpuId = sourceId,
    )

    // ==================== editOnSale 测试 ====================

    test("editOnSale - SPU 不存在时返回 SPU_NOT_FOUND") {
        whenever(spuRepository.findById(sourceSpuId)).thenReturn(null)

        val result = service.getDraft(sourceSpuId)

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe CommodityErrors.SPU_NOT_FOUND
    }

    test("editOnSale - 非 ON_SALE 状态返回 ONLY_ON_SALE_NEEDS_DRAFT") {
        val draftSpu = SpuImpl(
            id = sourceSpuId,
            name = "草稿",
            description = "",
            _status = CommodityStatus.DRAFT,
            _skus = mutableListOf(createSku(1L)),
        )
        whenever(spuRepository.findById(sourceSpuId)).thenReturn(draftSpu)

        val result = service.getDraft(sourceSpuId)

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe CommodityErrors.ONLY_ON_SALE_NEEDS_DRAFT
    }

    test("editOnSale - 已有草稿时幂等返回已有草稿") {
        val source = createOnSaleSpu()
        val existingDraft = createDraftSpu()
        whenever(spuRepository.findById(sourceSpuId)).thenReturn(source)
        whenever(spuRepository.findDraftBySourceSpuId(sourceSpuId)).thenReturn(existingDraft)

        val result = service.getDraft(sourceSpuId)

        result.shouldBeInstanceOf<Success<Spu>>()
        result.value shouldBe existingDraft
        verify(spuFactory, never()).createDraftCopy(any())
        verify(spuRepository, never()).save(any())
    }

    test("editOnSale - 无草稿时创建新草稿副本") {
        val source = createOnSaleSpu()
        val newDraft = createDraftSpu()
        whenever(spuRepository.findById(sourceSpuId)).thenReturn(source)
        whenever(spuRepository.findDraftBySourceSpuId(sourceSpuId)).thenReturn(null)
        whenever(spuFactory.createDraftCopy(source)).thenReturn(Success(newDraft))
        whenever(spuRepository.save(newDraft)).thenReturn(newDraft)

        val result = service.getDraft(sourceSpuId)

        result.shouldBeInstanceOf<Success<Spu>>()
        result.value shouldBe newDraft
        verify(spuFactory).createDraftCopy(source)
        verify(spuRepository).save(newDraft)
    }

    // ==================== publishDraft 测试 ====================

    test("publishDraft - SPU 不存在时返回 SPU_NOT_FOUND") {
        whenever(spuRepository.findById(draftSpuId)).thenReturn(null)

        val result = service.publishDraft(draftSpuId)

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe CommodityErrors.SPU_NOT_FOUND
    }

    test("publishDraft - 非草稿副本返回 NOT_A_DRAFT_COPY") {
        val nonDraft = SpuImpl(
            id = draftSpuId,
            name = "普通商品",
            description = "",
            _status = CommodityStatus.DRAFT,
            _skus = mutableListOf(createSku(1L)),
            sourceSpuId = null,
        )
        whenever(spuRepository.findById(draftSpuId)).thenReturn(nonDraft)

        val result = service.publishDraft(draftSpuId)

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe CommodityErrors.NOT_A_DRAFT_COPY
    }

    test("publishDraft - 完整流程：合并、快照、删除草稿") {
        val source = createOnSaleSpu(version = 5L)
        val draft = createDraftSpu()
        val snapshot = SpuSnapshot(
            id = SpuSnapshotId(999L),
            merchantId = MerchantId(1),
            spuId = sourceSpuId,
            snapshotVersion = 6L,
            spuName = draft.name,
            description = draft.description,
            skuSnapshots = emptyList(),
            createdAt = LocalDateTime.now(),
        )

        whenever(spuRepository.findById(draftSpuId)).thenReturn(draft)
        whenever(spuRepository.findById(sourceSpuId)).thenReturn(source)
        whenever(snapshotFactory.createSnapshot(source)).thenReturn(snapshot)
        whenever(spuRepository.save(source)).thenReturn(source)
        whenever(snapshotRepository.save(snapshot)).thenReturn(snapshot)

        val result = service.publishDraft(draftSpuId)

        result.shouldBeInstanceOf<Success<SpuSnapshot>>()
        result.value shouldBe snapshot

        // 验证合并后源商品数据已更新
        source.name shouldBe draft.name
        source.description shouldBe draft.description
        source.version shouldBe 6L // 5 + 1

        // 验证持久化操作
        verify(spuRepository).save(source)
        verify(snapshotRepository).save(snapshot)
        verify(spuRepository).delete(draft)
    }

    // ==================== discardDraft 测试 ====================

    test("discardDraft - SPU 不存在时返回 SPU_NOT_FOUND") {
        whenever(spuRepository.findById(draftSpuId)).thenReturn(null)

        val result = service.discardDraft(draftSpuId)

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe CommodityErrors.SPU_NOT_FOUND
    }

    test("discardDraft - 非草稿副本返回 NOT_A_DRAFT_COPY") {
        val nonDraft = SpuImpl(
            id = draftSpuId,
            name = "普通商品",
            description = "",
            _status = CommodityStatus.DRAFT,
            _skus = mutableListOf(createSku(1L)),
            sourceSpuId = null,
        )
        whenever(spuRepository.findById(draftSpuId)).thenReturn(nonDraft)

        val result = service.discardDraft(draftSpuId)

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe CommodityErrors.NOT_A_DRAFT_COPY
    }

    test("discardDraft - 完整流程：删除草稿，源商品不受影响") {
        val source = createOnSaleSpu(version = 3L)
        val draft = createDraftSpu()

        // 记录源商品原始状态
        val originalName = source.name
        val originalDescription = source.description
        val originalSkus = source.skus.toList()
        val originalVersion = source.version
        val originalStatus = source.status

        whenever(spuRepository.findById(draftSpuId)).thenReturn(draft)

        val result = service.discardDraft(draftSpuId)

        result.shouldBeInstanceOf<Success<Unit>>()
        verify(spuRepository).delete(draft)

        // 验证源商品完全不受影响
        source.name shouldBe originalName
        source.description shouldBe originalDescription
        source.skus shouldBe originalSkus
        source.version shouldBe originalVersion
        source.status shouldBe originalStatus
    }

    // ==================== createOrUpdate ON_SALE 拦截测试 ====================

    test("createOrUpdate - ON_SALE 商品直接编辑被拦截") {
        val cmd = CommodityCreateCmd(
            spuId = sourceSpuId,
            merchantId = 1,
            spuName = "新名称",
            description = "新描述",
        )
        val onSaleSpu = createOnSaleSpu()
        whenever(spuRepository.findById(sourceSpuId)).thenReturn(onSaleSpu)

        val result = service.createOrUpdate(cmd)

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe CommodityErrors.ON_SALE_DIRECT_EDIT_REJECTED
        verify(spuFactory, never()).update(any(), any())
        verify(spuRepository, never()).save(any())
    }

    test("createOrUpdate - DRAFT 商品允许直接编辑") {
        val cmd = CommodityCreateCmd(
            spuId = sourceSpuId,
            merchantId = 1,
            spuName = "更新名称",
            description = "更新描述",
        )
        val draftSpu = SpuImpl(
            id = sourceSpuId,
            name = "旧名称",
            description = "旧描述",
            _status = CommodityStatus.DRAFT,
            _skus = mutableListOf(createSku(1L)),
        )
        val updatedSpu = SpuImpl(
            id = sourceSpuId,
            name = cmd.spuName,
            description = cmd.description,
            _status = CommodityStatus.DRAFT,
            _skus = mutableListOf(createSku(1L)),
        )
        whenever(spuRepository.findById(sourceSpuId)).thenReturn(draftSpu)
        whenever(spuFactory.update(cmd, draftSpu)).thenReturn(updatedSpu)
        whenever(spuRepository.save(updatedSpu)).thenReturn(updatedSpu)

        val result = service.createOrUpdate(cmd)

        result.shouldBeInstanceOf<Success<Spu>>()
        result.value shouldBe updatedSpu
    }

    test("createOrUpdate - OFF_SALE 商品允许直接编辑") {
        val cmd = CommodityCreateCmd(
            spuId = sourceSpuId,
            merchantId = 1,
            spuName = "更新名称",
            description = "更新描述",
        )
        val offSaleSpu = SpuImpl(
            id = sourceSpuId,
            name = "旧名称",
            description = "旧描述",
            _status = CommodityStatus.OFF_SALE,
            _skus = mutableListOf(createSku(1L)),
        )
        val updatedSpu = SpuImpl(
            id = sourceSpuId,
            name = cmd.spuName,
            description = cmd.description,
            _status = CommodityStatus.OFF_SALE,
            _skus = mutableListOf(createSku(1L)),
        )
        whenever(spuRepository.findById(sourceSpuId)).thenReturn(offSaleSpu)
        whenever(spuFactory.update(cmd, offSaleSpu)).thenReturn(updatedSpu)
        whenever(spuRepository.save(updatedSpu)).thenReturn(updatedSpu)

        val result = service.createOrUpdate(cmd)

        result.shouldBeInstanceOf<Success<Spu>>()
        result.value shouldBe updatedSpu
    }
})
