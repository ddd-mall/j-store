package com.jstore.goods.domain.inventory

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.math.BigDecimal

data class StorageCreateCMD(
    val commodityCode: CommodityCode,
    val quantity: BigDecimal = BigDecimal.ZERO,
) {
    fun verify(): Result<Boolean, BusinessError> {
        if (quantity < BigDecimal.ZERO) {
            return Failure(StorageErrors.INVALID_AMOUNT)
        }
        return Success(true)
    }
}
