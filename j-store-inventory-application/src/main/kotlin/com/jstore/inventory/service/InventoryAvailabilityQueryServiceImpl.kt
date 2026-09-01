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
package com.jstore.inventory.service

import com.jstore.inventory.api.InventoryAvailabilityInfo
import com.jstore.inventory.api.InventoryAvailabilityKey
import com.jstore.inventory.api.InventoryAvailabilityQueryService
import com.jstore.inventory.domain.FulfillmentNodeId
import com.jstore.inventory.domain.SkuId
import com.jstore.inventory.domain.StockPositionRepository

class InventoryAvailabilityQueryServiceImpl(private val positions: StockPositionRepository) : InventoryAvailabilityQueryService {
    override fun queryAvailability(keys: List<InventoryAvailabilityKey>): List<InventoryAvailabilityInfo> =
        keys.distinct().mapNotNull { key ->
            positions.findBySkuAndNode(SkuId(key.skuId), FulfillmentNodeId(key.fulfillmentNodeId))?.let {
                InventoryAvailabilityInfo(key.skuId, key.fulfillmentNodeId, it.availableToPromise, it.sourceVersion, it.persistenceVersion)
            }
        }
}
