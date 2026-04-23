package com.jstore.order.domain.order.persistence

import com.jstore.common.persistent.SnowFlakeId
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
    var quantity: BigDecimal,
    var unitPrice: BigDecimal,
    var totalPrice: BigDecimal,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1
    }

}
