package com.jstore.goods.domain.storage

import com.jstore.common.persistent.SnowFlakSequence
import org.springframework.stereotype.Component

@Component
class StorageFactory(
    private val storageRepository: StorageRepository,
    private val storageOperationRepository: StorageOperationRepository,
    private val snowFlakSequence: SnowFlakSequence
) {
    fun create(createCMD: StorageCreateCMD): Storage {
        return StorageImpl(
            id = createCMD.commodityCode,
            amount = createCMD.amount,
            version = 1,
            storageRepository = storageRepository,
            storageOperationRepository = storageOperationRepository,
            idGenerator = snowFlakSequence
        )
    }
}