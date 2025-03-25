package com.jstore.goods.domain.storage

import com.jstore.common.errors.BusinessError

object StorageErrors {
    val INSUFFICIENT_INVENTORY = BusinessError("库存不足", "business.insufficientInventory", 400)
    val INVALID_AMOUNT = BusinessError("库存参数错误", "business.invalidAmount", 400)
}
