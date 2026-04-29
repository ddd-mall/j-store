package com.jstore.goods.domain.commodity

import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

// Feature: goods-style-and-sku-code, Property 3: 详情 HTML 存储保持

/**
 * Property 3: 详情 HTML 存储保持
 *
 * For any 字符串（包括空字符串），调用 updateDetailHtml 后读取 detailHtml，
 * 返回的字符串应与输入完全相等。
 *
 * **Validates: Requirements 3.1, 3.2**
 */
class GoodsStyleDetailHtmlStoragePropertyTest : FunSpec({

    // Generator for arbitrary strings including empty, special characters, and HTML tags
    val arbitraryHtmlArb: Arb<String> = Arb.string(0..500)

    fun createGoodsStyle(): GoodsStyle {
        return GoodsStyleImpl(
            id = GoodsStyleId(1L),
            spuId = SpuId(1L),
            _mainImages = mutableListOf(),
            _detailHtml = "",
            _skuImages = mutableMapOf(),
        )
    }

    test("updateDetailHtml should store and return the exact input string for any arbitrary string") {
        checkAll(100, arbitraryHtmlArb) { html ->
            val goodsStyle = createGoodsStyle()

            val result = goodsStyle.updateDetailHtml(html)

            result.shouldBeInstanceOf<Success<Unit>>()
            goodsStyle.detailHtml shouldBe html
        }
    }
})
