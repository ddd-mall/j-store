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

import com.jstore.common.framework.AggregateRepository

interface StockPositionRepository : AggregateRepository<StockPositionId, StockPosition> {
    fun findBySkuAndNode(skuId: SkuId, nodeId: FulfillmentNodeId): StockPosition?
}

fun interface StockPositionGuard {
    fun lock(keys: List<StockPositionId>): List<StockPosition>
}

interface StockReservationRepository : AggregateRepository<StockReservationId, StockReservation> {
    fun findByBusinessKey(businessKey: String): StockReservation?

    fun findByOrderId(orderId: Long): List<StockReservation>
}
