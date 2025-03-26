package com.jstore.goods.domain.storage


import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.framework.Entity
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Id
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onSuccess
import com.jstore.goods.domain.storage.StorageErrors.INSUFFICIENT_INVENTORY
import com.jstore.goods.domain.storage.StorageErrors.INVALID_AMOUNT
import com.jstore.goods.domain.storage.StorageErrors.STORAGE_OPERATION_FAILED
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
interface Storage : Entity<CommodityCode> {
    /**
     * 扣减
     */
    fun deduct(bizCode: String, amount: BigDecimal): Result<StorageOperation, BusinessError>

    /**
     * 检查库存是否满足
     */
    fun checkSufficient(amount: BigDecimal): Boolean


    /**
     * 增加 (prepare)
     */
    fun add(amount: BigDecimal): Result<Boolean, BusinessError>
}

class StorageImpl(
    override val id: CommodityCode,
    var amount: BigDecimal,
    var version: Long,
    private val storageLock: StorageLock,
    private val storageRepository: StorageRepository,
    private val storageOperationRepository: StorageOperationRepository,
    private val idGenerator: SnowFlakSequence,
) : Storage {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class)
    }

    override fun deduct(bizCode: String, amount: BigDecimal): Result<StorageOperation, BusinessError> {
        verify(amount)
        storageOperationRepository.findByBizCode(bizCode)?.let {
            return Success(it)
        }

        return when (val lock = storageLock.lock(this.id)) {
            is Success -> {
                if (!lock.value.isAcquire()) {
                    return Failure(STORAGE_OPERATION_FAILED)
                }
                val storageOperation = createAndSaveStorageOperation()
                deductAndSave(amount)
                val result = Success(storageOperation)
                lock.onSuccess { it.unlock() }
                result
            }

            is Failure -> {
                log.error("[storage deduct failed] - ${lock.error.message}")
                Failure(
                    CommonBusinessError.INTERNAL_ERROR.msg(
                        lock.error.message ?: "acquire lock failure when deduct storage ${this.id}"
                    )
                )
            }
        }
    }

    override fun checkSufficient(amount: BigDecimal): Boolean {
        return this.amount < amount
    }

    private fun deductAndSave(amount: BigDecimal) {
        this.amount = this.amount.subtract(amount)
        storageRepository.save(this)
    }

    private fun verify(amount: BigDecimal) : Result<Boolean, BusinessError> {
        if (amount <= BigDecimal.ZERO) {
            return Failure(INVALID_AMOUNT)
        }

        if (!checkSufficient(amount)) {
            return Failure(INSUFFICIENT_INVENTORY)
        }
        return Success(true)
    }

    private fun createAndSaveStorageOperation(): StorageOperation {
        return storageOperationRepository.save(
            StorageOperationImpl(
                id = StorageOperationId(idGenerator.nextId()),
                amount = amount,
                status = StorageOperationStatus.PREPARED,
                commodityCode = this.id,
                storageRepository = storageRepository,
                storageOperationRepository = storageOperationRepository
            )
        )
    }

    override fun add(amount: BigDecimal): Result<Boolean, BusinessError> {
        this.amount = this.amount.add(amount)
        storageRepository.save(this)
        return Success(true)
    }

}
