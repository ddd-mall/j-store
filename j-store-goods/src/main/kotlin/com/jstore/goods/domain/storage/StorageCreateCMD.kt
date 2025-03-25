package com.jstore.goods.domain.storage

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.math.BigDecimal

data class StorageCreateCMD(
    val commodityCode: CommodityCode,
    val amount: BigDecimal = BigDecimal.ZERO,
) {
    fun verify(): Result<Boolean, BusinessError> {
        if (amount < BigDecimal.ZERO) {
            return Failure(StorageErrors.INVALID_AMOUNT)
        }
        return Success(true)
    }
}
