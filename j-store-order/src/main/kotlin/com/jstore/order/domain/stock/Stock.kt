package com.jstore.order.domain.stock


import com.jstore.common.errors.CommonErrors
import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockServiceACL
import com.jstore.order.domain.saleorder.SaleOrderId
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor


data class StockId(override val value: String) : Id<String>(value)

class Stock(
    var id: StockId?,
    val orderId: SaleOrderId,
    val goodsId: GoodsId,
    val amount: BigDecimal,
    var currentStatus: StockStatus = StockStatus.CREATED,
    var lastStatus: StockStatus = currentStatus,
    @Transient val stockServiceACL: StockServiceACL,
    @Transient val executor: Executor? = null,
) : Entity<StockId> {
    fun preDeduct(): CompletableFuture<Stock> {
        if (currentStatus != StockStatus.CREATED) {
            throw CommonErrors.ILLEGAL_STATE.to("库存当前状态不允许进行此操作")
        }
        val supplier = {
            this.id = stockServiceACL.preDeduct(goodsId, amount)
            lastStatus = currentStatus
            currentStatus = StockStatus.PRE_DEDUCTED
            this
        }
        return executor?.let { CompletableFuture.supplyAsync(supplier, it) } ?: CompletableFuture.supplyAsync(supplier)
    }

    fun deduct(): CompletableFuture<Stock> {
        if (currentStatus != StockStatus.PRE_DEDUCTED) {
            throw CommonErrors.ILLEGAL_STATE.to("库存当前状态不允许进行此操作")
        }
        id ?: throw CommonErrors.ILLEGAL_STATE.to("库存操作未初始化")
        val supplier = {
            stockServiceACL.deduct(id!!)
            lastStatus = currentStatus
            currentStatus = StockStatus.DEDUCTED
            this
        }
        return executor?.let { CompletableFuture.supplyAsync(supplier, it) } ?: CompletableFuture.supplyAsync(supplier)
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

    override fun id(): StockId? {
        return id
    }
}

enum class StockStatus {
    CREATED, PRE_DEDUCTED, DEDUCTED
}
