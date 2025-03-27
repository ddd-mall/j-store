package com.jstore.goods.domain.commodity.comand

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.SpuId

data class CommodityCreateCmd(
    val spuId: SpuId?,
    val spuName: String,
) {
    fun verify(): Result<Boolean, BusinessError> {
        return Success(true)
    }
}