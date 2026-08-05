package com.jstore.accounting.acl

import com.jstore.accounting.domain.journal.SourceDocument
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result

interface AccountingOrderService {
    fun getOrderAccountingInfo(orderId: String): Result<OrderAccountingInfo, BusinessError>

    fun getRefundableOriginalSource(orderId: String): Result<SourceDocument, BusinessError>
}
