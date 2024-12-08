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
@IdClass(SaleOrderItemIdClass::class)

open class SaleOrderItemPO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null
    @Id
    @SnowFlakeId
    @Column(name = "sale_order_item_id", nullable = false, unique = true, updatable = false)
    open var saleOrderItemId: Long? = null

    @Column(name = "sale_order_id", nullable = false, updatable = false)
    open var saleOrderId: Long? = null
    open var spuId: String? = null
    open var skuId: String? = null
    open var skuVersion: Long? = null
    open var count: Long? = null
    open var unitPrice: BigDecimal? = null
    open var totalPrice: BigDecimal? = null
}


open class SaleOrderItemIdClass : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1
    }
    private var id: Long? = null
    private val saleOrderItemId: Long? = null
}