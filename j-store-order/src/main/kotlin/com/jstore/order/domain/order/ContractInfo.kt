package com.jstore.order.domain.order

import com.jstore.common.utils.Result
import com.jstore.common.utils.Failure
import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Success

data class ContractInfo (
    val email: String? = null,
    val phoneNumber: PhoneNumber ? = null,
) {
    fun validate(): Result<ContractInfo, BusinessError> {
        if (email.isNullOrBlank() && phoneNumber == null) {
            return Failure(OrderErrors.CONTRACT_INFO_INVALID)
        }
        return Success(this)
    }
}