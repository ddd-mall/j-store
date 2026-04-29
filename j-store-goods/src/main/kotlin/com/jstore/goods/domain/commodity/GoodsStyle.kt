package com.jstore.goods.domain.commodity

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.Entity
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success

interface GoodsStyle : Entity<GoodsStyleId> {
    val spuId: SpuId
    val mainImages: List<String>
    val detailHtml: String
    val skuImages: Map<SkuId, List<String>>

    fun updateMainImages(images: List<String>): Result<Unit, BusinessError>
    fun updateDetailHtml(html: String): Result<Unit, BusinessError>
    fun updateSkuImages(skuId: SkuId, images: List<String>): Result<Unit, BusinessError>
}

class GoodsStyleImpl(
    override val id: GoodsStyleId,
    override val spuId: SpuId,
    private var _mainImages: MutableList<String>,
    private var _detailHtml: String,
    private val _skuImages: MutableMap<SkuId, List<String>>,
) : GoodsStyle {
    override val mainImages: List<String> get() = _mainImages.toList()
    override val detailHtml: String get() = _detailHtml
    override val skuImages: Map<SkuId, List<String>> get() = _skuImages.toMap()

    override fun updateMainImages(images: List<String>): Result<Unit, BusinessError> {
        if (images.size != images.distinct().size) {
            return Failure(CommodityErrors.DUPLICATE_IMAGE_KEY)
        }
        _mainImages = images.toMutableList()
        return Success(Unit)
    }

    override fun updateDetailHtml(html: String): Result<Unit, BusinessError> {
        _detailHtml = html
        return Success(Unit)
    }

    override fun updateSkuImages(skuId: SkuId, images: List<String>): Result<Unit, BusinessError> {
        if (images.size != images.distinct().size) {
            return Failure(CommodityErrors.DUPLICATE_IMAGE_KEY)
        }
        _skuImages[skuId] = images.toList()
        return Success(Unit)
    }
}
