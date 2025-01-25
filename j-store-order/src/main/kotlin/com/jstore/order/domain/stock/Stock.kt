package com.jstore.order.domain.stock


import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockServiceACL
import com.jstore.order.domain.saleorder.SaleOrderId
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean


data class StockId(override val value: String) : Id<String>(value)

class Stock(
    var id: StockId,
    val orderId: SaleOrderId,
    val goodsId: GoodsId,
    val quantity: BigDecimal,
    var currentStatus: StockStatus = StockStatus.CREATED,
    var lastStatus: StockStatus = currentStatus,

    private var outerStockId: String? = null,
    @Transient val stockServiceACL: StockServiceACL,
    @Transient val executor: Executor? = null,
    @Transient val allowedRollback: AtomicBoolean = AtomicBoolean(false)
) : Entity<StockId> {


    fun preDeduct(): CompletableFuture<Stock> {
        if (currentStatus != StockStatus.CREATED) {
            throw StockErrors.IllegalState
        }

        val futureStock = {
            this.outerStockId = stockServiceACL.preDeduct(goodsId, quantity)
            lastStatus = currentStatus
            currentStatus = StockStatus.PRE_DEDUCTED
            allowedRollback.set(true)
            this
        }
        return executor?.let { CompletableFuture.supplyAsync(futureStock, it) } ?: CompletableFuture.supplyAsync(
            futureStock
        )
    }


    fun deduct(): CompletableFuture<Stock> {
        if (currentStatus != StockStatus.PRE_DEDUCTED) {
            throw StockErrors.IllegalState
        }
        val futureStock = {
            stockServiceACL.deduct(outerStockId!!)
            lastStatus = currentStatus
            currentStatus = StockStatus.DEDUCTED
            allowedRollback.set(true)
            this
        }
        return executor?.let { CompletableFuture.supplyAsync(futureStock, it) } ?: CompletableFuture.supplyAsync(
            futureStock
        )
    }


    fun rollback(): CompletableFuture<Stock> {
        if (currentStatus == lastStatus) {
            return CompletableFuture.completedFuture(this)
        }

        return CompletableFuture.supplyAsync {
            if (!allowedRollback.compareAndSet(true, false)) {
                return@supplyAsync this
            }
            stockServiceACL.rollback(outerStockId!!)
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
