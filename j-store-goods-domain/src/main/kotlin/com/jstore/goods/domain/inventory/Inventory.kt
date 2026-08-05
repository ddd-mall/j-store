/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.goods.domain.inventory

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.math.BigDecimal

data class CommodityCode(override val value: Long) : Id<Long>(value)

/**
 * TCC 模式下的库存模型
 *
 * 幂等性：使用bizCode作为幂等的key，若同一个bizCode已经有操作过库存，则返回之前的操作记录
 *
 * 并发安全：通过storageLock保证并发安全，StorageLock在不同的应用形式中可以有不同的实现，在分布式系统中可以通过分布式锁等
 */
interface Inventory : Entity<CommodityCode> {
    /** 预扣减 */
    fun reserve(amount: BigDecimal): Result<Boolean, BusinessError>

    fun deduct(amount: BigDecimal): Result<Boolean, BusinessError>

    fun release(amount: BigDecimal): Result<Boolean, BusinessError>

    /** 增加 (prepare) */
    fun add(quantity: BigDecimal): Result<Boolean, BusinessError>
}

class InventoryImpl(
    override val id: CommodityCode,
    private var availableQuantity: BigDecimal = BigDecimal.ZERO,
    private var reservedQuantity: BigDecimal = BigDecimal.ZERO,
    private val version: Long = 0,
) : Inventory {
    override fun reserve(amount: BigDecimal): Result<Boolean, BusinessError> {
        if (availableQuantity < amount) {
            return Failure(StorageErrors.INSUFFICIENT_INVENTORY)
        }
        availableQuantity -= amount
        reservedQuantity += amount
        return Success(true)
    }

    override fun deduct(amount: BigDecimal): Result<Boolean, BusinessError> {
        if (reservedQuantity < amount) {
            return Failure(
                StorageErrors.STORAGE_OPERATION_FAILED.msg(
                    "inventory deduct failed! because insufficient reserved inventor"
                )
            )
        }
        reservedQuantity -= amount
        return Success(true)
    }

    override fun release(amount: BigDecimal): Result<Boolean, BusinessError> {
        if (reservedQuantity < amount) {
            return Failure(
                StorageErrors.STORAGE_OPERATION_FAILED.msg(
                    "inventory release failed! because insufficient reserved inventor"
                )
            )
        }
        reservedQuantity -= amount
        availableQuantity += amount
        return Success(true)
    }

    override fun add(quantity: BigDecimal): Result<Boolean, BusinessError> {
        availableQuantity += quantity
        return Success(true)
    }
}
