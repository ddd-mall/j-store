package com.jstore.accounting.domain.settlement

import com.jstore.common.errors.BusinessError

object SettlementErrors {
    val SETTLEMENT_STATEMENT_INVALID_STATE = BusinessError("结算单状态不合法", "Accounting.Settlement.InvalidState", 400)
    val SETTLEMENT_AMOUNT_MISMATCH = BusinessError("结算金额不一致", "Accounting.Settlement.AmountMismatch", 400)
    val SETTLEMENT_STATEMENT_DUPLICATED = BusinessError("结算单已存在", "Accounting.Settlement.Duplicated", 409)
    val SETTLEMENT_STATEMENT_NOT_FOUND = BusinessError("结算单不存在", "Accounting.Settlement.NotFound", 404)
}
