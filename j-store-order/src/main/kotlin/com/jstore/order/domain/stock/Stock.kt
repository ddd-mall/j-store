package com.jstore.order.domain.stock


import com.jstore.common.framework.Entity
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.properties.Id
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.StockServiceACL
import com.jstore.order.domain.saleorder.SaleOrderId
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean


data class StockId(override val value: String) : Id<String>(value)

class Stock(
    var id: StockId,
    val orderId: SaleOrderId,
    val goodsId: GoodsId,
    val quantity: BigDecimal,
    var currentStatus: StockStatus = StockStatus.CREATED,
    var lastStatus: StockStatus = currentStatus,
    var outerStockId: String? = null,
    @Transient private val stockServiceACL: StockServiceACL,
) : Entity<StockId> {
    private val rollbackAble: AtomicBoolean = AtomicBoolean(false)
    private val log: Logger = LoggerFactory.getLogger(this::class)

    fun preDeduct() {
        if (currentStatus != StockStatus.CREATED) {
            throw StockErrors.IllegalState
        }
        this.outerStockId = stockServiceACL.preDeduct(goodsId, quantity)

        lastStatus = currentStatus
        currentStatus = StockStatus.PRE_DEDUCTED
        rollbackAble.set(true)
        log.info("stock $id has been pre deduct success, goodsId: $goodsId, quantity: $quantity")
    }


    fun deduct() {
        if (currentStatus != StockStatus.PRE_DEDUCTED) {
            throw StockErrors.IllegalState
        }
        lastStatus = currentStatus
        currentStatus = StockStatus.DEDUCTED
        rollbackAble.set(true)
    }


    fun rollback() {
        if (currentStatus == lastStatus || !rollbackAble.compareAndSet(true, false)) {
            return
        }

        val temp = currentStatus
        currentStatus = lastStatus
        lastStatus = temp
    }


    override fun id(): StockId {
        return id
    }
}

enum class StockStatus {
    CREATED, PRE_DEDUCTED, DEDUCTED
}
