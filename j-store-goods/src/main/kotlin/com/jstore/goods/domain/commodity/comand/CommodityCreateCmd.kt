package com.jstore.goods.domain.commodity.comand

import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.SpuId

data class CommodityCreateCmd(
    val spuId: SpuId?,
    val merchantId: Long,
    val spuName: String,
    val description: String = "",
) {
    fun verify(): Result<Boolean, BusinessError> {
        if (merchantId <= 0) {
            return Failure(CommonBusinessError.INVALID_PARAM.msg("商户ID必须为正数"))
        }
        if (spuName.isBlank()) {
            return Failure(CommonBusinessError.INVALID_PARAM.msg("商品名称不能为空"))
        }
        return Success(true)
    }
}
