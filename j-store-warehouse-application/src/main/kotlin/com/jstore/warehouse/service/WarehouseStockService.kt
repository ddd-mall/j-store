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
package com.jstore.warehouse.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.warehouse.domain.PhysicalStockId
import com.jstore.warehouse.domain.PhysicalStockRepository
import com.jstore.warehouse.domain.WarehouseErrors

interface WarehouseStockUseCase {
    fun adjust(stockId: PhysicalStockId, quantity: Int, reason: String): Result<Unit, BusinessError>
}

class WarehouseStockService(
    private val stocks: PhysicalStockRepository,
    private val publisher: DomainEventPublisher,
) : WarehouseStockUseCase {
    override fun adjust(
        stockId: PhysicalStockId,
        quantity: Int,
        reason: String,
    ): Result<Unit, BusinessError> {
        val stock = stocks.findById(stockId) ?: return Failure(WarehouseErrors.NOT_FOUND)
        stock.adjustTo(quantity, reason).onFailure {
            return Failure(it)
        }
        stocks.save(stock)
        stock.publishPendingEvents(publisher)
        return Success(Unit)
    }
}
