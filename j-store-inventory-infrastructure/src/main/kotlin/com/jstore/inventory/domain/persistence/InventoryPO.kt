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
package com.jstore.inventory.domain.persistence

import com.jstore.inventory.domain.StockReservationStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.Instant

@Entity
@Table(name = "inventory_stock_positions")
class StockPositionPO(
    @Id @Column(length = 192) var id: String = "",
    @Column(name = "sku_id", nullable = false) var skuId: Long = 0,
    @Column(name = "fulfillment_node_id", nullable = false, length = 128)
    var fulfillmentNodeId: String = "",
    @Column(name = "on_hand", nullable = false) var onHand: Int = 0,
    @Column(nullable = false) var reserved: Int = 0,
    @Column(name = "safety_stock", nullable = false) var safetyStock: Int = 0,
    @Column(name = "isolated_quantity", nullable = false) var isolatedQuantity: Int = 0,
    @Column(name = "source_version", nullable = false) var sourceVersion: Long = 0,
    @Version var persistenceVersion: Long = 0,
)

@Entity
@Table(
    name = "inventory_stock_reservations",
    uniqueConstraints = [UniqueConstraint(columnNames = ["business_key"])],
)
class StockReservationPO(
    @Id @Column(length = 256) var id: String = "",
    @Column(name = "business_key", nullable = false, length = 256) var businessKey: String = "",
    @Column(name = "order_id", nullable = false) var orderId: Long = 0,
    @Column(name = "sale_authorization_id", nullable = false, length = 128)
    var saleAuthorizationId: String = "",
    @Column(name = "sku_id", nullable = false) var skuId: Long = 0,
    @Column(name = "fulfillment_node_id", nullable = false, length = 128)
    var fulfillmentNodeId: String = "",
    @Column(nullable = false) var quantity: Int = 0,
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant = Instant.EPOCH,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: StockReservationStatus = StockReservationStatus.RESERVED,
    @Version var persistenceVersion: Long = 0,
)
