package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.goods.domain.storage.*
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class StorageService(
    private val storageRepository: StorageRepository,
    private val storageOperationRepository: StorageOperationRepository,
    private val storageFactory: StorageFactory
) {

    fun create(cmd: StorageCreateCMD): Result<Storage, BusinessError> {
        return when (val verifyResult = cmd.verify()) {
            is Success -> {
                val storage = storageFactory.create(cmd)
                Success(storageRepository.save(storage))
            }

            is Failure -> verifyResult
        }
    }

    fun perDeduct(commodityCode: CommodityCode, amount: BigDecimal): Result<StorageOperationId, BusinessError> {
        val storage = storageRepository.findById(commodityCode) ?: return Failure(
            CommonBusinessError.INVALID_PARAM.msg("can not find storage with commodityCode $commodityCode")
        )
        return when (val result = storage.preDeduct(amount)) {
            is Success -> Success(result.value.id)
            is Failure -> Failure(result.error)
        }
    }

    fun confirm(operationId: StorageOperationId): Result<Boolean, BusinessError> {
        val storageOperation = storageOperationRepository.findById(operationId) ?: return Failure(
            CommonBusinessError.INVALID_PARAM.msg("can not find pre deducted storage with id $operationId")
        )
        return storageOperation.confirm()
    }

    fun cancel(operationId: StorageOperationId): Result<Boolean, BusinessError> {
        val storageOperation = storageOperationRepository.findById(operationId) ?: return Failure(
            CommonBusinessError.INVALID_PARAM.msg("can not find pre deducted storage with id $operationId")
        )
        return storageOperation.cancel()
    }
}