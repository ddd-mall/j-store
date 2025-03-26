package com.jstore.goods.domain.storage

import com.jstore.common.persistent.SnowFlakSequence
import org.springframework.stereotype.Component

@Component
class StorageFactory(
    private val storageLock: StorageLock,
    private val storageRepository: StorageRepository,
    private val storageOperationRepository: StorageOperationRepository,
    private val snowFlakSequence: SnowFlakSequence,

) {
    fun create(createCMD: StorageCreateCMD): Storage {
        TODO()
    }
}