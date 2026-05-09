package com.jstore.accounting.domain.account

import com.jstore.common.errors.BusinessError

object AccountingAccountErrors {
    val ACCOUNT_NOT_FOUND = BusinessError("账务账户不存在", "Accounting.Account.NotFound", 404)
    val LEDGER_ACCOUNT_INACTIVE = BusinessError("账务账户已停用", "Accounting.Account.Inactive", 400)
    val LEDGER_ACCOUNT_CODE_DUPLICATED = BusinessError("账务账户编码重复", "Accounting.Account.CodeDuplicated", 409)
}
