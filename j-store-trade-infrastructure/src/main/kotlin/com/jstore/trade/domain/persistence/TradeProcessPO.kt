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
package com.jstore.trade.domain.persistence

import com.jstore.trade.domain.TradeProcessStatus
import jakarta.persistence.*
import java.time.Instant

@Embeddable
class TradeItemPO(
    @Column(name = "offer_id", nullable = false) var offerId: Long = 0,
    @Column(name = "store_id", nullable = false) var storeId: Long = 0,
    @Column(name = "spu_id", nullable = false) var spuId: Long = 0,
    @Column(name = "sku_id", nullable = false) var skuId: Long = 0,
    @Column(nullable = false) var quantity: Int = 0,
    @Column(name = "catalog_snapshot_version", nullable = false)
    var catalogSnapshotVersion: Long = 0,
    @Column(name = "offer_version", nullable = false) var offerVersion: Long = 0,
    @Column(name = "fulfillment_node_id", nullable = false, length = 128)
    var fulfillmentNodeId: String = "",
    @Column(name = "channel_id", nullable = false, length = 64) var channelId: String = "",
    @Column(name = "unit_price_fen", nullable = false) var unitPriceFen: Long = 0,
)

@Embeddable
class TradeAuthorizationPO(
    @Column(name = "authorization_id", nullable = false, length = 128)
    var authorizationId: String = "",
    @Column(name = "offer_id", nullable = false) var offerId: Long = 0,
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant = Instant.EPOCH,
)

@Embeddable
class TradeReservationPO(
    @Column(name = "reservation_id", nullable = false, length = 192) var reservationId: String = ""
)

@Entity
@Table(name = "trade_processes")
class TradeProcessPO(
    @Id var id: Long = 0,
    @Column(name = "order_id", nullable = false, unique = true) var orderId: Long = 0,
    @Column(name = "merchant_id", nullable = false) var merchantId: Long = 0,
    @Column(name = "payable_amount_fen", nullable = false) var payableAmountFen: Long = 0,
    @Column(nullable = false, length = 3) var currency: String = "CNY",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: TradeProcessStatus = TradeProcessStatus.AUTHORIZING,
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "trade_process_items", joinColumns = [JoinColumn(name = "trade_id")])
    @OrderColumn(name = "line_no")
    var items: MutableList<TradeItemPO> = mutableListOf(),
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "trade_process_authorizations",
        joinColumns = [JoinColumn(name = "trade_id")],
    )
    @OrderColumn(name = "line_no")
    var authorizations: MutableList<TradeAuthorizationPO> = mutableListOf(),
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "trade_process_reservations",
        joinColumns = [JoinColumn(name = "trade_id")],
    )
    @OrderColumn(name = "line_no")
    var reservations: MutableList<TradeReservationPO> = mutableListOf(),
    @Column(name = "reservation_expires_at") var reservationExpiresAt: Instant? = null,
    @Column(name = "failure_reason", length = 1024) var failureReason: String? = null,
    @Column(name = "close_reason", length = 1024) var closeReason: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.EPOCH,
    @Version var persistenceVersion: Long = 0,
)
