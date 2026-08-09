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
package com.jstore.warehouse.domain.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "warehouse_physical_stock")
class PhysicalStockPO(
    @Id @Column(length = 192) var id: String = "",
    @Column(name = "sku_id", nullable = false) var skuId: Long = 0,
    @Column(name = "fulfillment_node_id", nullable = false, length = 128)
    var fulfillmentNodeId: String = "",
    @Column(name = "on_hand", nullable = false) var onHand: Int = 0,
    @Column(name = "source_version", nullable = false) var sourceVersion: Long = 0,
    @Version var persistenceVersion: Long = 0,
)
