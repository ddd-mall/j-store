package com.jstore.order.domain.order.persistence

import com.jstore.order.domain.order.OrderItemStatus
import com.jstore.order.domain.order.TradeStatus
import com.jstore.order.domain.order.PaymentStatus
import com.jstore.order.domain.order.FulfillmentStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Convert
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
import jakarta.persistence.Version
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
class OrderPO(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "buyer_uid", nullable = false)
    var buyerUid: Long = 0,

    @Column(name = "buyer_phone", length = 20)
    var buyerPhone: String? = null,

    @Column(name = "buyer_name", length = 64)
    var buyerName: String? = null,

    @Convert(converter = RecipientInfoPOConverter::class)
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

    @Column(name = "total_refunded_amount", nullable = false, precision = 19, scale = 0)
    var totalRefundedAmount: BigDecimal = BigDecimal.ZERO,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 0)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "actual_pay", nullable = false, precision = 19, scale = 0)
    var actualPay: BigDecimal = BigDecimal.ZERO,

    @Column(name = "create_time", nullable = false)
    var createTime: LocalDateTime = LocalDateTime.now(),

    @Column(name = "update_time", nullable = false)
    var updateTime: LocalDateTime = LocalDateTime.now(),

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    var items: MutableList<OrderItemPO> = mutableListOf(),

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    var refundFacts: MutableList<OrderRefundFactPO> = mutableListOf(),
)

@Entity
@Table(name = "order_refund_facts", uniqueConstraints = [UniqueConstraint(columnNames = ["order_id", "after_sale_id", "order_item_id"])])
class OrderRefundFactPO(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    @Column(name = "order_id", insertable = false, updatable = false) var orderId: Long = 0,
    @Column(name = "after_sale_id", nullable = false) var afterSaleId: Long = 0,
    @Column(name = "order_item_id", nullable = false) var orderItemId: Long = 0,
    @Column(nullable = false) var quantity: Int = 0,
    @Column(nullable = false, precision = 19, scale = 0) var amount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "occurred_at", nullable = false) var occurredAt: java.time.Instant = java.time.Instant.EPOCH,
)

@Entity
@Table(name = "order_items")
class OrderItemPO(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "order_id", nullable = false, insertable = false, updatable = false)
    var orderId: Long = 0,

    @Column(name = "sku_id", nullable = false)
    var skuId: Long = 0,

    @Column(name = "spu_id", nullable = false)
    var spuId: Long = 0,

    @Column(name = "goods_name", nullable = false, length = 256)
    var goodsName: String = "",

    @Column(name = "sku_description", nullable = false, length = 512)
    var skuDescription: String = "",

    @Column(name = "quantity", nullable = false)
    var quantity: Int = 0,

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 0)
    var unitPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "snapshot_version", nullable = false)
    var snapshotVersion: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: OrderItemStatus = OrderItemStatus.NONE,

    @Column(name = "refunded_quantity", nullable = false)
    var refundedQuantity: Int = 0,

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 0)
    var refundedAmount: BigDecimal = BigDecimal.ZERO,
)
