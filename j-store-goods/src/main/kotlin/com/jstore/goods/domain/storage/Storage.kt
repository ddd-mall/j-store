package com.jstore.goods.domain.storage


import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.Entity
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Id
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.goods.domain.storage.StorageErrors.INSUFFICIENT_INVENTORY
import com.jstore.goods.domain.storage.StorageErrors.INVALID_AMOUNT
import java.math.BigDecimal

data class CommodityCode(override val value: Long) : Id<Long>(value)

/**
 * TCC 模式下的库存模型
 */
interface Storage : Entity<CommodityCode> {
    /**
     * 预扣（prepare）
     */
    fun preDeduct(amount: BigDecimal): Result<StorageOperation, BusinessError>

    /**
     * 增加 (prepare)
     */
    fun add(amount: BigDecimal): Result<Boolean, BusinessError>
}

class StorageImpl(
    override val id: CommodityCode,
    var amount: BigDecimal,
    var version: Long,
    private val storageRepository: StorageRepository,
    private val storageOperationRepository: StorageOperationRepository,
    private val idGenerator: SnowFlakSequence
) : Storage {
    override fun preDeduct(amount: BigDecimal): Result<StorageOperation, BusinessError> {
        if (amount <= BigDecimal.ZERO) {
            return Failure(INVALID_AMOUNT)
        }
        if (this.amount < amount) {
            return Failure(INSUFFICIENT_INVENTORY)
        }
        this.amount = this.amount.subtract(amount)
        storageRepository.save(this)

        return Success(
            storageOperationRepository.save(
                StorageOperationImpl(
                    id = StorageOperationId(idGenerator.nextId()),
                    amount = amount,
                    status = StorageOperationStatus.PREPARED,
                    commodityCode = this.id,
                    storageRepository = storageRepository,
                    storageOperationRepository = storageOperationRepository
                )
            )
        )
    }

    override fun add(amount: BigDecimal): Result<Boolean, BusinessError> {
        this.amount = this.amount.add(amount)
        storageRepository.save(this)
        return Success(true)
    }

}
