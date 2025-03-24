package com.jstore.goods.domain.storage

import com.jstore.common.framework.Repository
import com.jstore.goods.domain.spu.SkuId


interface StorageRepository : Repository<SkuId, Storage> {
}