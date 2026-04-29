package com.jstore.accounting.acl

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result

interface AccountingPaymentService {
    fun getPaymentAccountingInfo(orderId: String): Result<PaymentAccountingInfo, BusinessError>
}
