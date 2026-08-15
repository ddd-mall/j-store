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
package com.jstore.order.domain.order.persistence

import com.jstore.order.domain.order.CommitmentStatus
import com.jstore.order.domain.order.FulfillmentStatus
import com.jstore.order.domain.order.OrderItemStatus
import com.jstore.order.domain.order.PaymentStatus
import com.jstore.order.domain.order.TradeStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.LocalDateTime
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "orders")
class OrderPO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "source_trade_id") var sourceTradeId: Long? = null,
    @Column(name = "source_order_plan_id", unique = true) var sourceOrderPlanId: Long? = null,
    @Column(name = "source_plan_digest", length = 80) var sourcePlanDigest: String? = null,
    @Column(name = "merchant_id", nullable = false) var merchantId: Long = 0,
    @Column(name = "buyer_uid", nullable = false) var buyerUid: Long = 0,
    @Column(name = "buyer_phone", length = 20) var buyerPhone: String? = null,
    @Column(name = "buyer_name", length = 64) var buyerName: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipient_info", columnDefinition = "jsonb")
    var recipientInfo: RecipientInfoPO? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "trade_status", nullable = false, length = 32)
    var tradeStatus: TradeStatus = TradeStatus.CREATED,
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    var paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false, length = 32)
    var fulfillmentStatus: FulfillmentStatus = FulfillmentStatus.UNFULFILLED,
    @Enumerated(EnumType.STRING)
    @Column(name = "commitment_status", nullable = false, length = 32)
    var commitmentStatus: CommitmentStatus = CommitmentStatus.PENDING_OFFER,
    @Column(name = "currency", nullable = false, length = 3) var currency: String = "CNY",
    @Column(name = "items_subtotal", nullable = false, precision = 19, scale = 0)
    var itemsSubtotal: BigDecimal = BigDecimal.ZERO,
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 0)
    var discountAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "shipping_amount", nullable = false, precision = 19, scale = 0)
    var shippingAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 0)
    var taxAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "payable_amount", nullable = false, precision = 19, scale = 0)
    var payableAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 0)
    var paidAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 0)
    var refundedAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "payment_reference", length = 64, unique = true)
    var paymentReference: String? = null,
    @Column(name = "fulfillment_reference", length = 64, unique = true)
    var fulfillmentReference: String? = null,
    @Version @Column(name = "version", nullable = false) var version: Long = 0,
    @Column(name = "create_time", nullable = false)
    var createTime: LocalDateTime = LocalDateTime.now(),
    @Column(name = "update_time", nullable = false)
    var updateTime: LocalDateTime = LocalDateTime.now(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    var items: MutableList<OrderItemPO> = mutableListOf(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    var refundFacts: MutableList<OrderRefundFactPO> = mutableListOf(),
)

@Entity
@Table(
    name = "order_refund_facts",
    uniqueConstraints =
        [UniqueConstraint(columnNames = ["order_id", "refund_id", "order_item_id"])],
)
class OrderRefundFactPO(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    @Column(name = "order_id", insertable = false, updatable = false) var orderId: Long = 0,
    @Column(name = "refund_id", nullable = false, length = 64) var refundId: String = "",
    @Column(name = "after_sale_id", nullable = false) var afterSaleId: Long = 0,
    @Column(name = "order_item_id", nullable = false) var orderItemId: Long = 0,
    @Column(nullable = false) var quantity: Int = 0,
    @Column(nullable = false, precision = 19, scale = 0) var amount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: java.time.Instant = java.time.Instant.EPOCH,
)

@Entity
@Table(name = "order_items")
class OrderItemPO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "order_id", nullable = false, insertable = false, updatable = false)
    var orderId: Long = 0,
    @Column(name = "offer_id", nullable = false) var offerId: Long = 0,
    @Column(name = "store_id", nullable = false) var storeId: Long = 0,
    @Column(name = "offer_version", nullable = false) var offerVersion: Long = 1,
    @Column(name = "fulfillment_node_id", nullable = false, length = 128)
    var fulfillmentNodeId: String = "DEFAULT",
    @Column(name = "channel_id", nullable = false, length = 64) var channelId: String = "ONLINE",
    @Column(name = "sku_id", nullable = false) var skuId: Long = 0,
    @Column(name = "spu_id", nullable = false) var spuId: Long = 0,
    @Column(name = "goods_name", nullable = false, length = 256) var goodsName: String = "",
    @Column(name = "sku_description", nullable = false, length = 512)
    var skuDescription: String = "",
    @Column(name = "quantity", nullable = false) var quantity: Int = 0,
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 0)
    var unitPrice: BigDecimal = BigDecimal.ZERO,
    @Column(name = "snapshot_version", nullable = false) var snapshotVersion: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: OrderItemStatus = OrderItemStatus.NONE,
    @Column(name = "refunded_quantity", nullable = false) var refundedQuantity: Int = 0,
    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 0)
    var refundedAmount: BigDecimal = BigDecimal.ZERO,
)
