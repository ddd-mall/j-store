package com.jstore.goods.domain.storage

import com.jstore.common.framework.Repository

interface StorageOperationRepository: Repository<StorageOperationId ,StorageOperation> {
    fun findByBizCode(bizCode: String): StorageOperation?
}