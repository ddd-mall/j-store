package com.jstore.goods.domain.commodity

import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

// Feature: goods-style-and-sku-code, Property 4: SKU 图片列表顺序保持

/**
 * Property 4: SKU 图片列表顺序保持
 *
 * For any SkuId 和不含重复元素的 ImageKey 列表，调用 updateSkuImages 后读取 skuImages[skuId]，
 * 返回的列表应与输入列表完全相等（元素和顺序均一致）。
 *
 * **Validates: Requirements 4.1, 4.2, 4.3**
 */
class GoodsStyleSkuImagesOrderPropertyTest : FunSpec({

    val skuIdArb: Arb<SkuId> = Arb.long(1L..10000L).map { SkuId(it) }

    // Generator for a distinct ImageKey list (including empty lists)
    val distinctImageKeysArb: Arb<List<String>> = Arb.list(Arb.string(1..30), 0..20)
        .map { it.distinct() }

    fun createGoodsStyle(): GoodsStyle {
        return GoodsStyleImpl(
            id = GoodsStyleId(1L),
            spuId = SpuId(1L),
            _mainImages = mutableListOf(),
            _detailHtml = "",
            _skuImages = mutableMapOf(),
        )
    }

    test("updateSkuImages should preserve element order and content for any SkuId and distinct ImageKey list") {
        checkAll(100, skuIdArb, distinctImageKeysArb) { skuId, images ->
            val goodsStyle = createGoodsStyle()

            val result = goodsStyle.updateSkuImages(skuId, images)

            result.shouldBeInstanceOf<Success<Unit>>()
            goodsStyle.skuImages[skuId] shouldBe images
        }
    }
})
