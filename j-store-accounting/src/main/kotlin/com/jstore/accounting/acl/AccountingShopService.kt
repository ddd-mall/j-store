package com.jstore.accounting.acl

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result

interface AccountingShopService {
    fun getShopAccountingInfo(merchantId: String): Result<ShopAccountingInfo, BusinessError>
}
