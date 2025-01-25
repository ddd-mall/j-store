package com.jstore.com.jstore.order.domain.stock.persistent

import com.jstore.common.persistent.jpa.hibernate.SnowFlakeId
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockServiceACL
import com.jstore.order.domain.saleorder.SaleOrderId
import com.jstore.order.domain.stock.Stock
import com.jstore.order.domain.stock.StockId
import com.jstore.order.domain.stock.StockStatus
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
class StockPO : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }

    @Id
    @SnowFlakeId
    var id: String = ""
    @Column(name = "order_id", nullable = false, updatable = false)
    var orderId: Long = 0
    var spuId: Long = 0
    var skuId: Long = 0
    var quantity: BigDecimal = BigDecimal.ZERO
    var currentStatus: StockStatus = StockStatus.CREATED
    var lastStatus: StockStatus = StockStatus.CREATED
    @CreatedDate
    @Column(name = "create_time", updatable = false, insertable = true)
    lateinit var createTime: LocalDateTime
    @LastModifiedDate
    @Column(name = "update_time", nullable = false, updatable = true, insertable = true)
    lateinit var updateTime: LocalDateTime



    fun toStock(stockAclService: StockServiceACL): Stock {
        return Stock(
            id = StockId(id),
            orderId = SaleOrderId(orderId),
            goodsId = GoodsId(spuId, skuId),
            quantity = quantity,
            currentStatus = currentStatus,
            lastStatus = lastStatus,
            stockServiceACL = stockAclService
        )
    }

    constructor()
    constructor(stock: Stock) : this() {
        this.id = stock.id.value
        this.quantity = stock.quantity
        this.spuId = stock.goodsId.spuId
        this.skuId = stock.goodsId.skuId
        this.currentStatus = stock.currentStatus
        this.lastStatus = stock.lastStatus

    }
}