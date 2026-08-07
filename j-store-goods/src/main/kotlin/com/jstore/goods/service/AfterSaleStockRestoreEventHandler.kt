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
package com.jstore.goods.service

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.utils.onFailure
import com.jstore.goods.acl.event.AfterSaleStockRestoreRequestedEvent
import com.jstore.goods.domain.inventory.CommodityCode
import java.math.BigDecimal

class AfterSaleStockRestoreEventHandler(
    private val inventoryServiceProvider: () -> InventoryService?
) : DomainEventListener<AfterSaleStockRestoreRequestedEvent> {
    constructor(inventoryService: InventoryService) : this({ inventoryService })

    override fun listenerId() = "goods.after-sale-stock-restore.v1"

    override fun onDomainEvent(event: AfterSaleStockRestoreRequestedEvent) {
        val inventoryService =
            inventoryServiceProvider()
                ?: throw IllegalStateException("inventory service is not configured")
        event.items.forEach {
            inventoryService.add(CommodityCode(it.skuId), BigDecimal(it.quantity)).onFailure {
                throw IllegalStateException("after-sale stock restore failed: ${it.errorCode}")
            }
        }
    }
}
