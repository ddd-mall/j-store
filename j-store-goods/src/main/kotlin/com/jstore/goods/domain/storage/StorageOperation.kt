package com.jstore.goods.domain.storage

import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError.ILLEGAL_STATE
import com.jstore.common.errors.CommonBusinessError.INTERNAL_ERROR
import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.math.BigDecimal


interface StorageOperation : Entity<StorageOperationId> {
    fun confirm(): Result<Boolean, BusinessError>
    fun cancel(): Result<Boolean, BusinessError>
}

data class StorageOperationId(override val value: Long) : Id<Long>(value)
class StorageOperationImpl(
    override val id: StorageOperationId,
    val amount: BigDecimal,
    var status: StorageOperationStatus,
    val commodityCode: CommodityCode,
    private val storageRepository: StorageRepository,
    private val storageOperationRepository: StorageOperationRepository
) : StorageOperation {
    override fun confirm(): Result<Boolean, BusinessError> {
        if (StorageOperationStatus.PREPARED != this.status) {
            return Failure(ILLEGAL_STATE.msg("storage has been expired"))
        }
        this.status = StorageOperationStatus.CONFIRMED
        storageOperationRepository.save(this)
        return Success(true)
    }

    override fun cancel(): Result<Boolean, BusinessError> {
        val storage = storageRepository.findById(this.commodityCode)
            ?: return Failure(INTERNAL_ERROR.msg("Could not find storage with commodity code"))

        return when (val addResult = storage.add(this.amount)) {
            is Success -> {
                storageRepository.save(storage)
                this.status = StorageOperationStatus.CANCELLED
                storageOperationRepository.save(this)
                Success(true)
            }
            is Failure -> Failure(addResult.error)
        }
    }
}

enum class StorageOperationStatus {
    PREPARED,
    CONFIRMED,
    CANCELLED,
}
