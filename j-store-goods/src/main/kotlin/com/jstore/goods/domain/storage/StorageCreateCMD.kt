package com.jstore.goods.domain.storage

import com.jstore.common.errors.BusinessError
import java.math.BigDecimal

data class StorageCreateCMD(
    val commodityCode: CommodityCode,
    val amount: BigDecimal = BigDecimal.ZERO,
) {
    fun verify(): BusinessError? {
        if (amount < BigDecimal.ZERO) {
            return StorageErrors.INVALID_AMOUNT
        }
        return null
    }
}
