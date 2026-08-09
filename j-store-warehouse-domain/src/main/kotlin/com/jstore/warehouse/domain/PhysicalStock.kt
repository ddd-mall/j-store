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
package com.jstore.warehouse.domain

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.EventRecordingAggregateRoot
import com.jstore.common.properties.Id
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.warehouse.domain.event.PhysicalStockChangedEvent

data class PhysicalStockId(override val value: String) : Id<String>(value) {
    init {
        require(value.isNotBlank())
    }
}

object WarehouseErrors {
    val INVALID_QUANTITY = BusinessError("实物库存数量无效", "Warehouse.Stock.InvalidQuantity", 400)
    val NOT_FOUND = BusinessError("实物库存不存在", "Warehouse.Stock.NotFound", 404)
}

class PhysicalStock(
    override val id: PhysicalStockId,
    val skuId: Long,
    val fulfillmentNodeId: String,
    onHand: Int,
    sourceVersion: Long = 0,
    val persistenceVersion: Long = 0,
) : EventRecordingAggregateRoot<PhysicalStockId>() {
    private var _onHand = onHand
    private var _sourceVersion = sourceVersion

    val onHand: Int
        get() = _onHand

    val sourceVersion: Long
        get() = _sourceVersion

    init {
        require(skuId > 0 && fulfillmentNodeId.isNotBlank() && onHand >= 0 && sourceVersion >= 0)
    }

    fun adjustTo(quantity: Int, reason: String): Result<Unit, BusinessError> {
        if (quantity < 0 || reason.isBlank()) return Failure(WarehouseErrors.INVALID_QUANTITY)
        if (_onHand == quantity) return Success(Unit)
        _onHand = quantity
        _sourceVersion++
        raise(
            PhysicalStockChangedEvent(
                stockId = id,
                skuId = skuId,
                fulfillmentNodeId = fulfillmentNodeId,
                onHand = quantity,
                sourceVersion = _sourceVersion,
                reason = reason,
            )
        )
        return Success(Unit)
    }
}
