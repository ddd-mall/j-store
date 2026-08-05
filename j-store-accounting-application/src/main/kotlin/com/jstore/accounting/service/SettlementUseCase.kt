package com.jstore.accounting.service

import com.jstore.accounting.domain.settlement.SettlementStatement
import com.jstore.accounting.domain.settlement.SettlementStatementId
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import java.time.Instant

interface SettlementUseCase {
    fun confirmStatement(statementId: SettlementStatementId): Result<SettlementStatement, BusinessError>
    fun markPaid(statementId: SettlementStatementId, paidAt: Instant): Result<SettlementStatement, BusinessError>
}
