package com.jstore.goods.domain.storage


import com.jstore.common.framework.Entity
import com.jstore.goods.domain.spu.SkuId
import java.math.BigDecimal

/**
 * TCC 模式下的库存模型
 */
interface Storage : Entity<SkuId> {
    /**
     * 预扣 (Try)
     */
    fun preDeduct(amount: BigDecimal): StockOperation

    /**
     * 增加（Try）
     */
    fun add(amount: BigDecimal): StockOperation

    /**
     * 确认操作（执行操作）
     */
    fun confirm(opId: StockOperationId): Boolean

    /**
     * 取消操作
     */
    fun cancel(opId: StockOperationId): Boolean
}

class StorageImpl(
    override val id: SkuId,
) : Storage {
    override fun preDeduct(amount: BigDecimal): StockOperation {
        TODO("Not yet implemented")
    }

    override fun add(amount: BigDecimal): StockOperation {
        TODO("Not yet implemented")
    }

    override fun confirm(opId: StockOperationId): Boolean {
        TODO("Not yet implemented")
    }

    override fun cancel(opId: StockOperationId): Boolean {
        TODO("Not yet implemented")
    }
}
