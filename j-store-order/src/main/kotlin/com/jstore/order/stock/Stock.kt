package com.jstore.order.stock

import com.jstore.com.jstore.framework.Entity
import com.jstore.common.errors.CommonErrors
import com.jstore.common.properties.Id
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockAclService
import com.jstore.order.saleorder.SaleOrderId
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture

/**
 * 库存操作，以后可以引入分布式锁，保证库存操作原子性
 */
interface Stock : Entity<StockId> {
    var id: StockId?
    val orderId: SaleOrderId
    val goodsId: GoodsId
    val amount: BigDecimal
    fun preDeduct(): CompletableFuture<Stock>
    fun deduct(): CompletableFuture<Stock>
    fun rollback(): CompletableFuture<Stock>
}

data class StockId(override val value: String) : Id<String>(value)

class StockImpl(
    override var id: StockId?,
    override val orderId: SaleOrderId,
    override val goodsId: GoodsId,
    override val amount: BigDecimal,
    private val stockAclService: StockAclService,
    var currentStatus: StockStatus = StockStatus.CREATED,
    var lastStatus: StockStatus = currentStatus
) : Stock {
    override fun preDeduct(): CompletableFuture<Stock> {
        if (currentStatus != StockStatus.CREATED) {
            throw CommonErrors.ILLEGAL_STATE.to("库存当前状态不允许进行此操作")
        }
        return CompletableFuture.supplyAsync {
            this.id = stockAclService.preDeduct(goodsId, amount)
            lastStatus = currentStatus
            currentStatus = StockStatus.PRE_DEDUCTED
            this
        }
    }

    override fun deduct(): CompletableFuture<Stock> {
        if (currentStatus != StockStatus.PRE_DEDUCTED) {
            throw CommonErrors.ILLEGAL_STATE.to("库存当前状态不允许进行此操作")
        }
        id ?: throw CommonErrors.ILLEGAL_STATE.to("库存操作未初始化")
        return CompletableFuture.supplyAsync {
            stockAclService.deduct(id!!)
            lastStatus = currentStatus
            currentStatus = StockStatus.DEDUCTED
            this
        }
    }

    override fun rollback(): CompletableFuture<Stock> {
        id ?: throw CommonErrors.ILLEGAL_STATE.to("库存操作未初始化")
        return CompletableFuture.supplyAsync {
            stockAclService.rollback(id!!)
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
