package com.jstore.order.domain.order.persistence

import com.jstore.order.domain.order.OrderItemStatus
import com.jstore.order.domain.order.OrderStatus
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
    @Column(name = "status", nullable = false, length = 32)
    var status: OrderStatus = OrderStatus.PENDING_STOCK,

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 32)
    var previousStatus: OrderStatus? = null,

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

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_item_status", length = 32)
    var previousItemStatus: OrderItemStatus? = null,
)
