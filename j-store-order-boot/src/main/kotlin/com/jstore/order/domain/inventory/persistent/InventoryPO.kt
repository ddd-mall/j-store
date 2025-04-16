package com.jstore.com.jstore.order.domain.inventory.persistent

import com.jstore.order.acl.GoodsId
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.inventory.Inventory
import com.jstore.order.domain.inventory.InventoryId
import com.jstore.order.domain.inventory.InventoryStatus
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "order_stock",
    uniqueConstraints = [
        jakarta.persistence.UniqueConstraint(columnNames = ["order_id", "spu_id", "sku_id"])
    ],
)
class InventoryPO(
    @Id
    val id: Long,
    @Column(name = "order_id", nullable = false, updatable = false)
    var orderId: Long,
    var spuId: Long,
    var skuId: Long,
    var quantity: BigDecimal,
    @Enumerated(EnumType.STRING)
    var currentStatus: InventoryStatus = InventoryStatus.CREATED,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }

    @CreatedDate
    @Column(name = "create_time", updatable = false, insertable = true)
    lateinit var createTime: LocalDateTime

    @LastModifiedDate
    @Column(name = "update_time", nullable = false, updatable = true, insertable = true)
    lateinit var updateTime: LocalDateTime


    fun toInventory(): Inventory {
        return Inventory(
            id = InventoryId(id),
            orderId = OrderId(orderId),
            goodsId = GoodsId(spuId, skuId),
            quantity = quantity,
            status = currentStatus,
        )
    }


    constructor(inventory: Inventory) : this(
        id = inventory.id.value,
        orderId = inventory.orderId.value,
        quantity = inventory.quantity,
        spuId = inventory.goodsId.spuId,
        skuId = inventory.goodsId.skuId,
        currentStatus = inventory.status,
    )
}