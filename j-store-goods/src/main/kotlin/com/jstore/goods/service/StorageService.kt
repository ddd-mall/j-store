package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
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

    fun deduct(bizCode: String, commodityCode: CommodityCode, amount: BigDecimal): Result<StorageOperation, BusinessError> {
        val storage = storageRepository.findById(commodityCode)
            ?: return Failure(StorageErrors.STORAGE_DOSE_NOT_EXIST.msg("库存 $commodityCode 不存在"))
        when (val deduct = storage.deduct(bizCode, amount)) {
            is Success -> {}
            is Failure -> {}
        }
        storageRepository.save(storage)
        TODO()
    }


}