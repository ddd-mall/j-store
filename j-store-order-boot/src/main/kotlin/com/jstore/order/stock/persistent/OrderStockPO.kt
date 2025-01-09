package com.jstore.com.jstore.order.stock.persistent

import com.jstore.common.persistent.jpa.hibernate.SnowFlakeId
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockAclService
import com.jstore.order.saleorder.SaleOrderId
import com.jstore.order.stock.Stock
import com.jstore.order.stock.StockId
import com.jstore.order.stock.StockImpl
import com.jstore.order.stock.StockStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.io.Serializable
import java.math.BigDecimal

@Entity
@Table(
    name = "order_stock",
    uniqueConstraints = [
        jakarta.persistence.UniqueConstraint(columnNames = ["order_id", "spu_id", "sku_id"])
    ],
)
class OrderStockPO : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
    @Id
    @SnowFlakeId
    private var id: String = ""
    @Column(name = "order_id", nullable = false, updatable = false)
    private var orderId: Long = 0
    private var spuId: Long = 0
    private var skuId: Long = 0
    private var amount: BigDecimal = BigDecimal.ZERO
    private var currentStatus: StockStatus = StockStatus.CREATED
    private var lastStatus: StockStatus = StockStatus.CREATED

    constructor()

    constructor(stock: Stock) {
        this.orderId = stock.orderId.value
        this.spuId = stock.goodsId.spuId
        this.skuId = stock.goodsId.skuId
        this.amount = stock.amount
        this.currentStatus = stock.currentStatus
        this.lastStatus = stock.lastStatus
    }

    fun toStock(stockAclService: StockAclService): StockImpl {
        return StockImpl(
            id = StockId(id),
            orderId = SaleOrderId(orderId),
            goodsId = GoodsId(spuId, skuId),
            amount = amount,
            currentStatus = currentStatus,
            lastStatus = lastStatus,
            stockAclService = stockAclService
        )
    }
}