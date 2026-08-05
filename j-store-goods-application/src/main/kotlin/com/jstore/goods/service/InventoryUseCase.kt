package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.goods.domain.inventory.CommodityCode
import com.jstore.goods.domain.inventory.Inventory
import com.jstore.goods.domain.inventory.ReservationRecord
import com.jstore.goods.domain.inventory.StorageCreateCMD
import java.math.BigDecimal

/** 库存上下文对外暴露的应用用例端口。 */
interface InventoryUseCase {
    fun create(cmd: StorageCreateCMD): Result<Inventory, BusinessError>

    fun reserve(
        bizCode: String,
        commodityCode: CommodityCode,
        amount: BigDecimal,
    ): Result<ReservationRecord, BusinessError>

    fun confirm(bizCode: String): Result<Boolean, BusinessError>

    fun release(bizCode: String): Result<Boolean, BusinessError>

    fun add(commodityCode: CommodityCode, quantity: BigDecimal): Result<Boolean, BusinessError>
}
