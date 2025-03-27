package com.jstore.goods.domain.commodity.comand

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.goods.domain.commodity.SpuId

class SKUAppendCMD(
    val spuId: SpuId,
) {

    fun verify(): Result<SKUAppendCMD, BusinessError> {
        TODO("")
    }
}