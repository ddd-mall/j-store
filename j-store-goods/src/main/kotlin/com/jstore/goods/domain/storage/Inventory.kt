package com.jstore.goods.domain.storage


import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.Entity
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.properties.Id
import com.jstore.common.utils.Result
import java.math.BigDecimal

data class CommodityCode(override val value: Long) : Id<Long>(value)

/**
 * TCC 模式下的库存模型
 *
 * 幂等性：使用bizCode作为幂等的key，若同一个bizCode已经有操作过库存，则返回之前的操作记录
 *
 * 并发安全：通过storageLock保证并发安全，StorageLock在不同的应用形式中可以有不同的实现，在分布式系统中可以通过分布式锁等
 *
 */
interface Inventory : Entity<CommodityCode> {
    /**
     * 预扣减
     */
    fun reserve(amount: BigDecimal): Result<Boolean, BusinessError>

    fun deduct(amount: BigDecimal): Result<Boolean, BusinessError>

    fun release(amount: BigDecimal): Result<Boolean, BusinessError>

    /**
     * 增加 (prepare)
     */
    fun add(amount: BigDecimal): Result<Boolean, BusinessError>
}

class InventoryImpl(
    override val id: CommodityCode,

) : Inventory {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class)
    }

    override fun reserve(amount: BigDecimal):  Result<Boolean, BusinessError> {
        TODO("Not yet implemented")
    }

    override fun deduct(amount: BigDecimal): Result<Boolean, BusinessError> {
        TODO("Not yet implemented")
    }

    override fun release(amount: BigDecimal): Result<Boolean, BusinessError> {
        TODO("Not yet implemented")
    }

    override fun add(amount: BigDecimal): Result<Boolean, BusinessError> {
        TODO("Not yet implemented")
    }


}
