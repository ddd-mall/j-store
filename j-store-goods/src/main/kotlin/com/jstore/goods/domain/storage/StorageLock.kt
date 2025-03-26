package com.jstore.goods.domain.storage

import com.jstore.common.utils.Lock
import com.jstore.common.utils.Result
import java.util.concurrent.TimeUnit

interface StorageLock {
    fun lock(commodityCode: CommodityCode, timeout: Long, timeUnit: TimeUnit) : Result<Lock, Throwable>
    fun lock(commodityCode: CommodityCode) : Result<Lock, Throwable>
}