package com.jstore.goods.domain.commodity

import com.jstore.common.persistent.SnowFlakSequence

interface GoodsStyleFactory {
    fun create(
        spuId: SpuId,
        mainImages: List<String>,
        detailHtml: String,
        skuImages: Map<SkuId, List<String>>,
    ): GoodsStyle
}

class GoodsStyleFactoryImpl(
    private val snowFlakSequence: SnowFlakSequence,
) : GoodsStyleFactory {
    override fun create(
        spuId: SpuId,
        mainImages: List<String>,
        detailHtml: String,
        skuImages: Map<SkuId, List<String>>,
    ): GoodsStyle {
        return GoodsStyleImpl(
            id = GoodsStyleId(snowFlakSequence.nextId()),
            spuId = spuId,
            _mainImages = mainImages.toMutableList(),
            _detailHtml = detailHtml,
            _skuImages = skuImages.toMutableMap(),
        )
    }
}
