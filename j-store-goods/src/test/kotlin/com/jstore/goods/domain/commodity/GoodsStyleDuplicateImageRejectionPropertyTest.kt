package com.jstore.goods.domain.commodity

import com.jstore.common.utils.Failure
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

// Feature: goods-style-and-sku-code, Property 2: 重复图片标识拒绝

/**
 * Property 2: 重复图片标识拒绝
 *
 * For any 包含至少一个重复元素的 ImageKey 列表，调用 updateMainImages 或 updateSkuImages 应返回 Failure，且 GoodsStyle
 * 的原有状态不变。
 *
 * **Validates: Requirements 2.4, 4.4**
 */
class GoodsStyleDuplicateImageRejectionPropertyTest :
    FunSpec({

        // Generator: produces a list of ImageKeys that contains at least one duplicate
        val duplicateImageKeysArb: Arb<List<String>> =
            Arb.bind(
                Arb.list(Arb.string(1..20), 1..10),
                Arb.int(0..9),
            ) { baseList, dupIndex ->
                // Pick an element to duplicate and insert it at a random position
                val source = baseList[dupIndex.mod(baseList.size)]
                val insertPos = (dupIndex + 1).mod(baseList.size + 1)
                baseList.toMutableList().apply { add(insertPos, source) }
            }

        val skuIdArb: Arb<SkuId> = Arb.long(1L..10000L).map { SkuId(it) }

        fun createGoodsStyle(
            mainImages: List<String> = listOf("original-img-1", "original-img-2"),
            skuImages: Map<SkuId, List<String>> =
                mapOf(SkuId(99L) to listOf("sku-img-1", "sku-img-2")),
        ): GoodsStyleImpl {
            return GoodsStyleImpl(
                id = GoodsStyleId(1L),
                spuId = SpuId(1L),
                _mainImages = mainImages.toMutableList(),
                _detailHtml = "some detail",
                _skuImages = skuImages.toMutableMap(),
            )
        }

        test(
            "updateMainImages should return Failure and leave mainImages unchanged when given duplicate ImageKeys"
        ) {
            checkAll(100, duplicateImageKeysArb) { duplicateImages ->
                val originalMainImages = listOf("original-img-1", "original-img-2")
                val goodsStyle = createGoodsStyle(mainImages = originalMainImages)

                val result = goodsStyle.updateMainImages(duplicateImages)

                result.shouldBeInstanceOf<Failure<*>>()
                result.error shouldBe CommodityErrors.DUPLICATE_IMAGE_KEY
                goodsStyle.mainImages shouldBe originalMainImages
            }
        }

        test(
            "updateSkuImages should return Failure and leave skuImages unchanged when given duplicate ImageKeys"
        ) {
            checkAll(100, duplicateImageKeysArb, skuIdArb) { duplicateImages, skuId ->
                val originalSkuImages = mapOf(SkuId(99L) to listOf("sku-img-1", "sku-img-2"))
                val goodsStyle = createGoodsStyle(skuImages = originalSkuImages)

                val result = goodsStyle.updateSkuImages(skuId, duplicateImages)

                result.shouldBeInstanceOf<Failure<*>>()
                result.error shouldBe CommodityErrors.DUPLICATE_IMAGE_KEY
                goodsStyle.skuImages shouldBe originalSkuImages
            }
        }
    })
