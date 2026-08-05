package com.jstore.accounting.domain.journal

import com.jstore.common.errors.BusinessError

object AccountingErrors {
    val JOURNAL_ENTRY_UNBALANCED = BusinessError("账务凭证借贷不平", "Accounting.Journal.Unbalanced", 400)
    val JOURNAL_ENTRY_ALREADY_POSTED =
        BusinessError("已过账凭证不可修改", "Accounting.Journal.AlreadyPosted", 400)
    val JOURNAL_ENTRY_NOT_FOUND = BusinessError("账务凭证不存在", "Accounting.Journal.NotFound", 404)
    val SOURCE_DOCUMENT_ALREADY_POSTED =
        BusinessError("来源单据已入账", "Accounting.Journal.SourceAlreadyPosted", 409)
    val ACCOUNTING_PERIOD_CLOSED = BusinessError("会计期间已关闭", "Accounting.Journal.PeriodClosed", 400)
    val JOURNAL_ENTRY_LINES_INSUFFICIENT =
        BusinessError("账务凭证分录不足", "Accounting.Journal.LinesInsufficient", 400)
    val JOURNAL_LINE_AMOUNT_INVALID =
        BusinessError("账务分录金额无效", "Accounting.Journal.LineAmountInvalid", 400)
    val ACCOUNTING_PERIOD_NOT_FOUND =
        BusinessError("会计期间不存在", "Accounting.Journal.PeriodNotFound", 404)
    val JOURNAL_ENTRY_INVALID_STATE =
        BusinessError("账务凭证状态不合法", "Accounting.Journal.InvalidState", 400)
}
