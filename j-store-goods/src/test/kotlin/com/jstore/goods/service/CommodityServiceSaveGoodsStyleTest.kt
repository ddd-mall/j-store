package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.*
import com.jstore.goods.domain.commodity.comand.GoodsStyleSaveCmd
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactory
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.mockito.kotlin.*

class CommodityServiceSaveGoodsStyleTest : FunSpec({

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

    val spuId = SpuId(100L)

    // ==================== SPU 不存在返回错误 ====================

    test("saveGoodsStyle - SPU 不存在时返回 SPU_NOT_FOUND 错误") {
        val cmd = GoodsStyleSaveCmd(
            spuId = spuId,
            mainImages = listOf("img1", "img2"),
            detailHtml = "<p>detail</p>",
            skuImages = emptyMap(),
        )
        whenever(spuRepository.findById(spuId)).thenReturn(null)

        val result = service.saveGoodsStyle(cmd)

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe CommodityErrors.SPU_NOT_FOUND
        verify(goodsStyleRepository, never()).save(any())
    }

    // ==================== 新建 GoodsStyle ====================

    test("saveGoodsStyle - SPU 存在且无已有 GoodsStyle 时创建新记录") {
        val skuId = SkuId(1L)
        val cmd = GoodsStyleSaveCmd(
            spuId = spuId,
            mainImages = listOf("img1", "img2"),
            detailHtml = "<p>detail</p>",
            skuImages = mapOf(skuId to listOf("sku_img1")),
        )
        val spu: Spu = mock()
        val newGoodsStyle = GoodsStyleImpl(
            id = GoodsStyleId(999L),
            spuId = spuId,
            _mainImages = mutableListOf("img1", "img2"),
            _detailHtml = "<p>detail</p>",
            _skuImages = mutableMapOf(skuId to listOf("sku_img1")),
        )

        whenever(spuRepository.findById(spuId)).thenReturn(spu)
        whenever(goodsStyleRepository.findBySpuId(spuId)).thenReturn(null)
        whenever(goodsStyleFactory.create(any(), any(), any(), any())).thenReturn(newGoodsStyle)
        whenever(goodsStyleRepository.save(any<GoodsStyle>())).thenReturn(newGoodsStyle)

        val result = service.saveGoodsStyle(cmd)

        result.shouldBeInstanceOf<Success<GoodsStyle>>()
        result.value shouldBe newGoodsStyle
        verify(goodsStyleFactory).create(any(), any(), any(), any())
        verify(goodsStyleRepository).save(any<GoodsStyle>())
    }

    // ==================== 更新已有 GoodsStyle ====================

    test("saveGoodsStyle - SPU 存在且已有 GoodsStyle 时执行更新") {
        val skuId = SkuId(2L)
        val cmd = GoodsStyleSaveCmd(
            spuId = spuId,
            mainImages = listOf("new_img1", "new_img2"),
            detailHtml = "<p>updated</p>",
            skuImages = mapOf(skuId to listOf("sku_new_img1")),
        )
        val spu: Spu = mock()
        val existingGoodsStyle = GoodsStyleImpl(
            id = GoodsStyleId(888L),
            spuId = spuId,
            _mainImages = mutableListOf("old_img"),
            _detailHtml = "<p>old</p>",
            _skuImages = mutableMapOf(),
        )

        whenever(spuRepository.findById(spuId)).thenReturn(spu)
        whenever(goodsStyleRepository.findBySpuId(spuId)).thenReturn(existingGoodsStyle)
        whenever(goodsStyleRepository.save(any<GoodsStyle>())).thenReturn(existingGoodsStyle)

        val result = service.saveGoodsStyle(cmd)

        result.shouldBeInstanceOf<Success<GoodsStyle>>()
        result.value.mainImages shouldBe listOf("new_img1", "new_img2")
        result.value.detailHtml shouldBe "<p>updated</p>"
        // Verify skuImages was updated — use the same SkuId reference since Id doesn't override equals
        result.value.skuImages.entries.any { (k, v) ->
            k.value == skuId.value && v == listOf("sku_new_img1")
        } shouldBe true
        verify(goodsStyleFactory, never()).create(any(), any(), any(), any())
        verify(goodsStyleRepository).save(any<GoodsStyle>())
    }
})
