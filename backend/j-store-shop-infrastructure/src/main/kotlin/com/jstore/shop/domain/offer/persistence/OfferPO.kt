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
package com.jstore.shop.domain.offer.persistence

import com.jstore.shop.domain.offer.OfferStatus
import com.jstore.shop.domain.offer.SaleAuthorizationStatus
import com.jstore.shop.domain.offer.StoreStatus
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
@Table(name = "stores")
class StorePO(
    @Id var id: Long = 0,
    @Column(name = "merchant_id", nullable = false) var merchantId: Long = 0,
    @Column(nullable = false, length = 128) var name: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: StoreStatus = StoreStatus.ACTIVE,
    @Version var persistenceVersion: Long = 0,
)

@Entity
@Table(name = "sales_offers")
class SalesOfferPO(
    @Id var id: Long = 0,
    @Column(name = "store_id", nullable = false) var storeId: Long = 0,
    @Column(name = "merchant_id", nullable = false) var merchantId: Long = 0,
    @Column(name = "sku_id", nullable = false) var skuId: Long = 0,
    @Column(name = "channel_id", nullable = false, length = 64) var channelId: String = "",
    @Column(nullable = false, length = 32) var market: String = "",
    @Column(name = "price_fen", nullable = false) var priceFen: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: OfferStatus = OfferStatus.SUSPENDED,
    @Column(name = "starts_at", nullable = false) var startsAt: Instant = Instant.EPOCH,
    @Column(name = "ends_at") var endsAt: Instant? = null,
    @Column(name = "max_quantity_per_order", nullable = false) var maxQuantityPerOrder: Int = 1,
    @Column(name = "fulfillment_node_id", nullable = false, length = 128)
    var fulfillmentNodeId: String = "",
    @Column(name = "allow_backorder", nullable = false) var allowBackorder: Boolean = false,
    @Column(name = "offer_version", nullable = false) var offerVersion: Long = 1,
    @Version var persistenceVersion: Long = 0,
)

@Entity
@Table(
    name = "sale_authorizations",
    uniqueConstraints = [UniqueConstraint(columnNames = ["order_plan_id", "offer_id"])],
)
class SaleAuthorizationPO(
    @Id @Column(length = 128) var id: String = "",
    @Column(name = "trade_id", nullable = false) var tradeId: Long = 0,
    @Column(name = "order_plan_id", nullable = false) var orderPlanId: Long = 0,
    @Column(name = "offer_id", nullable = false) var offerId: Long = 0,
    @Column(name = "store_id", nullable = false) var storeId: Long = 0,
    @Column(name = "merchant_id", nullable = false) var merchantId: Long = 0,
    @Column(name = "sku_id", nullable = false) var skuId: Long = 0,
    @Column(nullable = false) var quantity: Int = 0,
    @Column(name = "offer_version", nullable = false) var offerVersion: Long = 0,
    @Column(name = "unit_price_fen", nullable = false) var unitPriceFen: Long = 0,
    @Column(name = "fulfillment_node_id", nullable = false, length = 128)
    var fulfillmentNodeId: String = "",
    @Column(name = "allow_backorder", nullable = false) var allowBackorder: Boolean = false,
    @Column(name = "authorized_at", nullable = false) var authorizedAt: Instant = Instant.EPOCH,
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant = Instant.EPOCH,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: SaleAuthorizationStatus = SaleAuthorizationStatus.AUTHORIZED,
    @Version var persistenceVersion: Long = 0,
)
