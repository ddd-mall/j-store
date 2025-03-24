package com.jstore.common.transaction

import org.apache.seata.rm.tcc.api.BusinessActionContext
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction


interface TCCResourceManager {
    @TwoPhaseBusinessAction(name = "prepare", commitMethod = "confirm", rollbackMethod = "cancel")
    fun perpare(businessActionContext: BusinessActionContext)
    fun confirm(businessActionContext: BusinessActionContext)
    fun cancel(businessActionContext: BusinessActionContext)
}