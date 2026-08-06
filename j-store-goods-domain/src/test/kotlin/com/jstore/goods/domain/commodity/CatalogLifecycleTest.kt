package com.jstore.goods.domain.commodity

import com.jstore.common.utils.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CatalogLifecycleTest {
    @Test
    fun `published catalog product can be archived without store sale semantics`() {
        val product =
            SpuImpl(
                id = SpuId(1),
                merchantId = MerchantId(7),
                name = "咖啡",
                _status = CommodityStatus.DRAFT,
                _skus = mutableListOf(SkuImpl(SkuId(11), "250g", emptyList())),
            )

        assertIs<Success<Unit>>(product.publish())
        assertEquals(CommodityStatus.PUBLISHED, product.status)
        assertIs<Success<Unit>>(product.archive())
        assertEquals(CommodityStatus.ARCHIVED, product.status)
    }
}
