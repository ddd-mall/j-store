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
package com.jstore.inventory.domain

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success

class StockPosition(
    override val id: StockPositionId,
    val skuId: SkuId,
    val fulfillmentNodeId: FulfillmentNodeId,
    onHand: Int,
    reserved: Int = 0,
    safetyStock: Int = 0,
    isolatedQuantity: Int = 0,
    sourceVersion: Long = 0,
    val persistenceVersion: Long = 0,
) : AggregateRoot<StockPositionId> {
    private var _onHand = onHand
    private var _reserved = reserved
    private var _safetyStock = safetyStock
    private var _isolatedQuantity = isolatedQuantity
    private var _sourceVersion = sourceVersion

    val onHand: Int
        get() = _onHand

    val reserved: Int
        get() = _reserved

    val safetyStock: Int
        get() = _safetyStock

    val isolatedQuantity: Int
        get() = _isolatedQuantity

    val sourceVersion: Long
        get() = _sourceVersion

    val availableToPromise: Int
        get() = (_onHand - _reserved - _safetyStock - _isolatedQuantity).coerceAtLeast(0)

    init {
        require(
            onHand >= 0 &&
                reserved >= 0 &&
                safetyStock >= 0 &&
                isolatedQuantity >= 0 &&
                reserved <= onHand &&
                sourceVersion >= 0
        )
    }

    fun reserve(quantity: Int): Result<Unit, BusinessError> {
        if (quantity <= 0) return Failure(InventoryErrors.INVALID_QUANTITY)
        if (quantity > availableToPromise) return Failure(InventoryErrors.INSUFFICIENT_ATP)
        _reserved += quantity
        return Success(Unit)
    }

    fun release(quantity: Int): Result<Unit, BusinessError> {
        if (quantity <= 0 || quantity > _reserved) {
            return Failure(InventoryErrors.INVALID_QUANTITY)
        }
        _reserved -= quantity
        return Success(Unit)
    }

    fun confirm(quantity: Int): Result<Unit, BusinessError> {
        if (quantity <= 0 || quantity > _reserved || quantity > _onHand) {
            return Failure(InventoryErrors.INVALID_QUANTITY)
        }
        _reserved -= quantity
        _onHand -= quantity
        return Success(Unit)
    }

    /** WMS 使用绝对数量和单调来源版本更新镜像；旧事件幂等忽略。 */
    fun applyPhysicalStock(onHand: Int, sourceVersion: Long): Boolean {
        require(onHand >= 0 && sourceVersion >= 0)
        if (sourceVersion <= _sourceVersion) return false
        _onHand = onHand
        _sourceVersion = sourceVersion
        return true
    }

    fun changeSafetyStock(quantity: Int): Result<Unit, BusinessError> {
        if (quantity < 0) return Failure(InventoryErrors.INVALID_QUANTITY)
        _safetyStock = quantity
        return Success(Unit)
    }

    fun changeIsolatedQuantity(quantity: Int): Result<Unit, BusinessError> {
        if (quantity < 0) return Failure(InventoryErrors.INVALID_QUANTITY)
        _isolatedQuantity = quantity
        return Success(Unit)
    }
}
