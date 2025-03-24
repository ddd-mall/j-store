package com.jstore.goods.domain.storage

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.transaction.TCCResourceManager
import org.apache.seata.rm.tcc.api.BusinessActionContext

class StockOperationId(override val value: Long): Id<Long> (value)

class StockOperation(
    override val id: StockOperationId,
): Entity<StockOperationId>, TCCResourceManager {
    override fun perpare(businessActionContext: BusinessActionContext) {
        TODO("Not yet implemented")
    }

    override fun confirm(businessActionContext: BusinessActionContext) {
        TODO("Not yet implemented")
    }

    override fun cancel(businessActionContext: BusinessActionContext) {
        TODO("Not yet implemented")
    }
}