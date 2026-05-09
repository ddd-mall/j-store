package com.jstore.goods.domain.inventory

import com.jstore.common.errors.BusinessError

object StorageErrors {
    val INSUFFICIENT_INVENTORY = BusinessError("库存不足", "business.insufficientInventory", 400)
    val INVALID_AMOUNT = BusinessError("库存参数错误", "business.invalidAmount", 400)
    val STORAGE_DOSE_NOT_EXIST = BusinessError("库存不存在", "business.storageDoesNotExist", 404)
    val STORAGE_OPERATION_FAILED = BusinessError("库存操作失败", "business.storageOperationFailed", 500)
    val RESERVATION_RECORD_NOT_FOUND = BusinessError("未能找到预扣记录", "business.reservationRecordNotFound", 404)
}
