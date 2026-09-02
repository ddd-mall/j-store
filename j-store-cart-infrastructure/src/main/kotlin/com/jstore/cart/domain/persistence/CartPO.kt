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
package com.jstore.cart.domain.persistence

import com.jstore.cart.domain.*
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "carts")
class CartPO(
    @Id var id: Long = 0,
    @Column(name = "buyer_id", nullable = false) var buyerId: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: CartStatus = CartStatus.ACTIVE,
    @Column(nullable = false, length = 32) var market: String = "",
    @Column(name = "channel_id", nullable = false, length = 64) var channelId: String = "",
    @Column(nullable = false, length = 3) var currency: String,
    @Column(name = "content_version", nullable = false) var contentVersion: Long = 0,
    @Version var persistenceVersion: Long = 0,
    @OneToMany(
        mappedBy = "cart",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER,
    )
    @OrderBy("id")
    var lines: MutableList<CartLinePO> = mutableListOf(),
)

@Entity
@Table(
    name = "cart_lines",
    uniqueConstraints = [UniqueConstraint(columnNames = ["cart_id", "offer_id"])],
)
class CartLinePO(
    @Id var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    var cart: CartPO? = null,
    @Column(name = "sku_id", nullable = false) var skuId: Long = 0,
    @Column(name = "offer_id", nullable = false) var offerId: Long = 0,
    @Column(name = "merchant_id", nullable = false) var merchantId: Long = 0,
    @Column(nullable = false) var quantity: Int = 0,
    @Column(nullable = false) var selected: Boolean = true,
    @Column(name = "added_at", nullable = false) var addedAt: Instant = Instant.EPOCH,
    @Column(name = "modified_at", nullable = false) var modifiedAt: Instant = Instant.EPOCH,
)

@Entity
@Table(
    name = "cart_assessments",
    uniqueConstraints = [UniqueConstraint(columnNames = ["cart_id", "source_cart_version"])],
)
class CartAssessmentPO(
    @Id var id: Long = 0,
    @Column(name = "cart_id", nullable = false) var cartId: Long = 0,
    @Column(name = "source_cart_version", nullable = false) var sourceCartVersion: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: AssessmentStatus = AssessmentStatus.EMPTY,
    @Column(name = "amount_fen", nullable = false) var amountFen: Long = 0,
    @Column(nullable = false, length = 3) var currency: String,
    @Column(name = "evaluated_at", nullable = false) var evaluatedAt: Instant = Instant.EPOCH,
    @OneToMany(
        mappedBy = "assessment",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER,
    )
    @OrderBy("cartLineId")
    var lines: MutableList<CartAssessmentLinePO> = mutableListOf(),
)

@Entity
@Table(
    name = "cart_assessment_lines",
    uniqueConstraints = [UniqueConstraint(columnNames = ["assessment_id", "cart_line_id"])],
)
class CartAssessmentLinePO(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    var assessment: CartAssessmentPO? = null,
    @Column(name = "cart_line_id", nullable = false) var cartLineId: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: LineAssessmentStatus = LineAssessmentStatus.UNSELECTED,
    @Column(name = "unit_price_fen") var unitPriceFen: Long? = null,
    @Column(name = "offer_version") var offerVersion: Long? = null,
    @Column(name = "catalog_version") var catalogVersion: Long? = null,
    @Column(name = "observed_atp") var observedAtp: Int? = null,
    @Column(name = "amount_fen", nullable = false) var amountFen: Long = 0,
)

@Entity
@Table(
    name = "cart_request_receipts",
    uniqueConstraints = [UniqueConstraint(columnNames = ["buyer_id", "request_id"])],
)
class CartRequestReceiptPO(
    @Id @Column(length = 256) var id: String = "",
    @Column(name = "buyer_id", nullable = false) var buyerId: Long = 0,
    @Column(name = "request_id", nullable = false, length = 128) var requestId: String = "",
    @Column(name = "request_digest", nullable = false, length = 64) var requestDigest: String = "",
    @Column(name = "cart_id", nullable = false) var cartId: Long = 0,
    @Column(name = "cart_version", nullable = false) var cartVersion: Long = 0,
)
