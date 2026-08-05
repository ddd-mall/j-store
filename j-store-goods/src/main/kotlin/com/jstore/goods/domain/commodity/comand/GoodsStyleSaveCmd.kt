package com.jstore.goods.domain.commodity.comand

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.CommodityErrors
import com.jstore.goods.domain.commodity.SkuId
import com.jstore.goods.domain.commodity.SpuId

data class GoodsStyleSaveCmd(
    val spuId: SpuId,
    val mainImages: List<String>,
    val detailHtml: String,
    val skuImages: Map<SkuId, List<String>>,
) {
    fun verify(): Result<Boolean, BusinessError> {
        if (mainImages.size != mainImages.distinct().size) {
            return Failure(CommodityErrors.DUPLICATE_IMAGE_KEY)
        }
        for ((_, images) in skuImages) {
            if (images.size != images.distinct().size) {
                return Failure(CommodityErrors.DUPLICATE_IMAGE_KEY)
            }
        }
        return Success(true)
    }
}
