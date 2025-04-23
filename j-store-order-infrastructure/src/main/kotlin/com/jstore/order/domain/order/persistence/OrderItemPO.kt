package com.jstore.order.domain.order.persistence

import com.jstore.common.persistent.jpa.hibernate.SnowFlakeId
import com.jstore.order.domain.order.OrderItemStatus
import jakarta.persistence.*
import java.io.Serializable
import java.math.BigDecimal

@Entity
@Table(
    name = "order_item",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_order_item_id", columnNames = ["order_item_id"])
    ],
    indexes = [
        Index(name = "idx_order_id", columnList = "order_id")
    ],
)

class OrderItemPO(
    @Id
    @SnowFlakeId
    @Column(name = "order_item_id", nullable = false, unique = true, updatable = false)
    val orderItemId: Long,
    @Column(name = "order_id", nullable = false, updatable = false)
    var orderId: Long,
    var spuId: String,
    var skuId: String,
    var goodsVersion: Long,
    var quantity: BigDecimal,
    var unitPrice: BigDecimal,
    var totalPrice: BigDecimal,
    @Enumerated(EnumType.STRING)
    var itemStatus: OrderItemStatus,
) : Serializable {
    constructor() : this(
        orderItemId = 0,
        orderId = 0,
        spuId = "",
        skuId = "",
        goodsVersion = 0,
        quantity = BigDecimal.ZERO,
        unitPrice = BigDecimal.ZERO,
        totalPrice = BigDecimal.ZERO,
        itemStatus = OrderItemStatus.NONE
    )

    companion object {
        private const val serialVersionUID: Long = 1
    }

}
