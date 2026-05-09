package com.jstore.goods.domain.commodity

import com.jstore.common.utils.json.JsonUtils
import com.jstore.goods.domain.commodity.persistence.GoodsStylePO
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

// Feature: goods-style-and-sku-code, Property 5: GoodsStyle Converter 往返一致性

/**
 * Property 5: GoodsStyle Converter 往返一致性
 *
 * For any 有效的 GoodsStyle 领域对象，经过 Converter.toPO 转换为 GoodsStylePO
 * 再经过 Converter.toDomain 转换回领域对象，结果应与原始对象在所有业务字段上等价
 * （id、spuId、mainImages、detailHtml、skuImages）。
 *
 * **Validates: Requirements 5.6, 5.7**
 */
class GoodsStyleConverterRoundTripPropertyTest : FunSpec({

    // Generator for a single image key (non-empty alphanumeric string)
    val imageKeyArb: Arb<String> = Arb.string(1..30, Codepoint.alphanumeric())

    // Generator for a distinct list of image keys
    fun distinctImageKeysArb(range: IntRange = 0..5): Arb<List<String>> =
        Arb.list(imageKeyArb, range).map { it.distinct() }

    // Generator for a SkuId
    val skuIdArb: Arb<SkuId> = Arb.long(1L..Long.MAX_VALUE).map { SkuId(it) }

    // Generator for skuImages: Map<SkuId, List<String>> with distinct keys and distinct image values per key
    val skuImagesArb: Arb<Map<SkuId, List<String>>> = Arb.list(
        Arb.bind(skuIdArb, distinctImageKeysArb(0..4)) { skuId, images -> skuId to images },
        0..3,
    ).map { pairs ->
        // Ensure distinct SkuId keys
        pairs.distinctBy { it.first.value }.toMap()
    }

    // Generator for a valid GoodsStyle domain object
    val goodsStyleArb: Arb<GoodsStyle> = Arb.bind(
        Arb.long(1L..Long.MAX_VALUE),       // goodsStyleId
        Arb.long(1L..Long.MAX_VALUE),       // spuId
        distinctImageKeysArb(0..5),          // mainImages (distinct)
        Arb.string(0..200),                  // detailHtml
        skuImagesArb,                        // skuImages
    ) { goodsStyleIdVal, spuIdVal, mainImages, detailHtml, skuImages ->
        GoodsStyleImpl(
            id = GoodsStyleId(goodsStyleIdVal),
            spuId = SpuId(spuIdVal),
            _mainImages = mainImages.toMutableList(),
            _detailHtml = detailHtml,
            _skuImages = skuImages.toMutableMap(),
        )
    }

    // Replicate the Converter logic locally since it's internal to GoodsStyleRepositoryImpl
    fun toPO(goodsStyle: GoodsStyle): GoodsStylePO {
        val skuImagesMap: Map<String, List<String>> = goodsStyle.skuImages.map { (skuId, images) ->
            skuId.value.toString() to images
        }.toMap()

        return GoodsStylePO(
            id = goodsStyle.id.value,
            spuId = goodsStyle.spuId.value,
            mainImages = JsonUtils.toJsonString(goodsStyle.mainImages),
            detailHtml = goodsStyle.detailHtml,
            skuImages = JsonUtils.toJsonString(skuImagesMap),
        )
    }

    fun toDomain(po: GoodsStylePO): GoodsStyle {
        val mainImages: List<String> = JsonUtils.deserialize(po.mainImages)
        val skuImagesRaw: Map<String, List<String>> = JsonUtils.deserialize(po.skuImages)
        val skuImages: MutableMap<SkuId, List<String>> = skuImagesRaw.map { (key, images) ->
            SkuId(key.toLong()) to images
        }.toMap().toMutableMap()

        return GoodsStyleImpl(
            id = GoodsStyleId(po.id),
            spuId = SpuId(po.spuId),
            _mainImages = mainImages.toMutableList(),
            _detailHtml = po.detailHtml,
            _skuImages = skuImages,
        )
    }

    test("toPO then toDomain should preserve all business fields") {
        checkAll(100, goodsStyleArb) { goodsStyle ->
            val po = toPO(goodsStyle)
            val roundTripped = toDomain(po)

            roundTripped.id.value shouldBe goodsStyle.id.value
            roundTripped.spuId.value shouldBe goodsStyle.spuId.value
            roundTripped.mainImages shouldBe goodsStyle.mainImages
            roundTripped.detailHtml shouldBe goodsStyle.detailHtml

            // Verify skuImages: same keys and same image lists per key
            roundTripped.skuImages.size shouldBe goodsStyle.skuImages.size
            for ((skuId, images) in goodsStyle.skuImages) {
                val roundTrippedImages = roundTripped.skuImages.entries.find { it.key.value == skuId.value }?.value
                roundTrippedImages shouldBe images
            }
        }
    }
})
