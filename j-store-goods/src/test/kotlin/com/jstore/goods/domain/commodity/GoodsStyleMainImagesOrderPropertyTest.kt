package com.jstore.goods.domain.commodity

import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

// Feature: goods-style-and-sku-code, Property 1: 主图列表顺序保持

/**
 * Property 1: 主图列表顺序保持
 *
 * For any 有效的不含重复元素的 ImageKey 列表（含空列表），调用 updateMainImages 后读取 mainImages，
 * 返回的列表应与输入列表完全相等（元素和顺序均一致）。
 *
 * **Validates: Requirements 1.3, 2.1, 2.2, 2.3**
 */
class GoodsStyleMainImagesOrderPropertyTest : FunSpec({

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

    test("updateMainImages should preserve element order and content for any distinct ImageKey list") {
        checkAll(100, distinctImageKeysArb) { images ->
            val goodsStyle = createGoodsStyle()

            val result = goodsStyle.updateMainImages(images)

            result.shouldBeInstanceOf<Success<Unit>>()
            goodsStyle.mainImages shouldBe images
        }
    }
})
