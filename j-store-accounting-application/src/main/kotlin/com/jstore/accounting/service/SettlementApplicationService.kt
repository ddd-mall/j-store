package com.jstore.accounting.service

import com.jstore.accounting.domain.settlement.SettlementErrors
import com.jstore.accounting.domain.settlement.SettlementStatement
import com.jstore.accounting.domain.settlement.SettlementStatementId
import com.jstore.accounting.domain.settlement.SettlementStatementRepository
import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import java.time.Instant

class SettlementApplicationService(
    private val settlementStatementRepository: SettlementStatementRepository,
    private val domainEventPublisher: DomainEventPublisher? = null,
) : SettlementUseCase {
    override fun confirmStatement(
        statementId: SettlementStatementId
    ): Result<SettlementStatement, BusinessError> {
        val statement =
            settlementStatementRepository.findById(statementId)
                ?: return Failure(SettlementErrors.SETTLEMENT_STATEMENT_NOT_FOUND)
        statement.confirm().onFailure {
            return Failure(it)
        }
        return Success(settlementStatementRepository.save(statement))
    }

    override fun markPaid(
        statementId: SettlementStatementId,
        paidAt: Instant,
    ): Result<SettlementStatement, BusinessError> {
        val statement =
            settlementStatementRepository.findById(statementId)
                ?: return Failure(SettlementErrors.SETTLEMENT_STATEMENT_NOT_FOUND)
        statement.markPaid(paidAt).onFailure {
            return Failure(it)
        }
        val saved = settlementStatementRepository.save(statement)
        domainEventPublisher?.let { statement.publishPendingEvents(it) }
        return Success(saved)
    }
}
