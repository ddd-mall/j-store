package com.jstore.order.domain.stock


import com.jstore.common.errors.CommonErrors
import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockServiceACL
import com.jstore.order.domain.saleorder.SaleOrderId
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture



data class StockId(override val value: String) : Id<String>(value)

class Stock(
    var id: StockId?,
    val orderId: SaleOrderId,
    val goodsId: GoodsId,
    val amount: BigDecimal,
    @Transient private val stockServiceACL: StockServiceACL,
    var currentStatus: StockStatus = StockStatus.CREATED,
    var lastStatus: StockStatus = currentStatus,

    ) : Entity<StockId> {
    fun preDeduct(): CompletableFuture<Stock> {
        if (currentStatus != StockStatus.CREATED) {
            throw CommonErrors.ILLEGAL_STATE.to("库存当前状态不允许进行此操作")
        }
        return CompletableFuture.supplyAsync {
            this.id = stockServiceACL.preDeduct(goodsId, amount)
            lastStatus = currentStatus
            currentStatus = StockStatus.PRE_DEDUCTED
            this
        }
    }

    fun deduct(): CompletableFuture<Stock> {
        if (currentStatus != StockStatus.PRE_DEDUCTED) {
            throw CommonErrors.ILLEGAL_STATE.to("库存当前状态不允许进行此操作")
        }
        id ?: throw CommonErrors.ILLEGAL_STATE.to("库存操作未初始化")
        return CompletableFuture.supplyAsync {
            stockServiceACL.deduct(id!!)
            lastStatus = currentStatus
            currentStatus = StockStatus.DEDUCTED
            this
        }
    }

    fun rollback(): CompletableFuture<Stock> {
        id ?: throw CommonErrors.ILLEGAL_STATE.to("库存操作未初始化")
        return CompletableFuture.supplyAsync {
            stockServiceACL.rollback(id!!)
            val temp = currentStatus
            currentStatus = lastStatus
            lastStatus = temp
            this
        }
    }

    override fun getId(): StockId? {
        return id
    }
}

enum class StockStatus {
    CREATED, PRE_DEDUCTED, DEDUCTED
}
