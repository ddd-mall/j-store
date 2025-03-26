package com.jstore.goods.domain.storage

import com.jstore.common.utils.Lock
import com.jstore.common.utils.Result
import java.util.concurrent.TimeUnit

interface InventoryLock {
    fun lock(commodityCode: CommodityCode, timeout: Long, timeUnit: TimeUnit) : Result<Lock, Throwable>
}

interface InventoryLockConfig {
    fun getLockTimeout(): Long = 5
    fun getLockTimeUnit(): TimeUnit = TimeUnit.MINUTES
}