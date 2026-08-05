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
package com.jstore.order.domain.aftersale

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRepository
import com.jstore.common.utils.Result
import com.jstore.order.domain.order.OrderId

interface AfterSaleRepository : AggregateRepository<AfterSaleId, AfterSale> {
    fun createWithAllocation(
        afterSale: AfterSale,
        ceilings: List<RefundCapacityCeiling>,
        receipt: AfterSaleCommandReceipt,
    ): Result<AfterSale, BusinessError>

    fun findByOrderId(orderId: OrderId): List<AfterSale>

    fun saveDecision(
        afterSale: AfterSale,
        allocationAction: AllocationAction,
        receipt: AfterSaleCommandReceipt,
    ): Result<AfterSale, BusinessError>

    fun findReceipt(
        actorId: Long,
        type: AfterSaleCommandType,
        key: String,
    ): AfterSaleCommandReceipt?
}
