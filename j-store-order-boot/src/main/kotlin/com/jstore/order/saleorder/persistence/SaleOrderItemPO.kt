package com.jstore.com.jstore.order.saleorder.persistence

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

open class SaleOrderItemPO : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1
    }

    @Id
    @SnowFlakeId
    @Column(name = "sale_order_item_id", nullable = false, unique = true, updatable = false)
    open var saleOrderItemId: Long = 0L
    @Column(name = "sale_order_id", nullable = false, updatable = false)
    open var saleOrderId: Long = 0L
    open var spuId: String = ""
    open var skuId: String = ""
    open var skuVersion: Long = 0L
    open var count: BigDecimal = BigDecimal.ZERO
    open var unitPrice: BigDecimal = BigDecimal.ZERO
    open var totalPrice: BigDecimal = BigDecimal.ZERO
}
