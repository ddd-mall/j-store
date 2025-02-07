package com.jstore.com.jstore.order.domain.stock.persistent

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
class StockPO(
    @Id
    val id: String,
    @Column(name = "order_id", nullable = false, updatable = false)
    var orderId: Long,
    var spuId: Long,
    var skuId: Long,
    var quantity: BigDecimal,
    var currentStatus: StockStatus = StockStatus.CREATED,
    var lastStatus: StockStatus = StockStatus.CREATED,
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


    fun toStock(stockServiceACL: StockServiceACL,): Stock {
        return Stock(
            id = StockId(id),
            orderId = SaleOrderId(orderId),
            goodsId = GoodsId(spuId, skuId),
            quantity = quantity,
            currentStatus = currentStatus,
            lastStatus = lastStatus,
            stockServiceACL = stockServiceACL
        )
    }


    constructor(stock: Stock) : this(
        id = stock.id.value,
        orderId = stock.orderId.value,
        quantity = stock.quantity,
        spuId = stock.goodsId.spuId,
        skuId = stock.goodsId.skuId,
        currentStatus = stock.currentStatus,
        lastStatus = stock.lastStatus,
    )
}