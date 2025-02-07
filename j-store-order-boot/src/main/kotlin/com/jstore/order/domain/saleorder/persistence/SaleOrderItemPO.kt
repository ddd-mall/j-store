package com.jstore.com.jstore.order.domain.saleorder.persistence

import com.jstore.common.persistent.jpa.hibernate.SnowFlakeId
import jakarta.persistence.*
import java.io.Serializable
import java.math.BigDecimal

@Entity
@Table(
    name = "sale_order_item",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_sale_order_item_id", columnNames = ["sale_order_item_id"])
    ],
    indexes = [
        Index(name = "idx_sale_order_id", columnList = "sale_order_id")
    ],
)

class SaleOrderItemPO(
    @Id
    @SnowFlakeId
    @Column(name = "sale_order_item_id", nullable = false, unique = true, updatable = false)
    val saleOrderItemId: Long,
    @Column(name = "sale_order_id", nullable = false, updatable = false)
    var saleOrderId: Long,
    var spuId: String,
    var skuId: String,
    var goodsVersion: Long,
    var quantity: BigDecimal,
    var unitPrice: BigDecimal,
    var totalPrice: BigDecimal,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1
    }
}
