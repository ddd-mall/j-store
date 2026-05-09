package com.jstore.accounting.domain.settlement

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.accounting.domain.settlement.event.SettlementPaidEvent
import java.time.Instant
import java.util.LinkedList
import java.util.Queue

class SettlementStatementImpl(
    override val id: SettlementStatementId,
    override val statementNo: String,
    override val merchantId: String,
    override val period: SettlementPeriod,
    private val _lines: MutableList<SettlementLine> = mutableListOf(),
    private var _status: SettlementStatementStatus = SettlementStatementStatus.DRAFT,
    private var _payableAmount: Price = Price.ZERO,
    private var _confirmedAt: Instant? = null,
    private var _paidAt: Instant? = null,
) : SettlementStatement {
    override val domainEventQueue: Queue<DomainEvent> = LinkedList()

    init {
        require(statementNo.isNotBlank()) { "结算单号不能为空" }
        require(merchantId.isNotBlank()) { "商户ID不能为空" }
    }

    override val status: SettlementStatementStatus get() = _status
    override val lines: List<SettlementLine> get() = _lines.toList()
    override val payableAmount: Price get() = _payableAmount
    override val confirmedAt: Instant? get() = _confirmedAt
    override val paidAt: Instant? get() = _paidAt

    override fun addLine(line: SettlementLine): Result<Unit, BusinessError> {
        if (_status != SettlementStatementStatus.DRAFT) {
            return Failure(SettlementErrors.SETTLEMENT_STATEMENT_INVALID_STATE)
        }
        _lines.add(line)
        _payableAmount = Price.sumOf(_lines.map { it.netAmount })
        return Success(Unit)
    }

    override fun confirm(): Result<Unit, BusinessError> {
        if (_status != SettlementStatementStatus.DRAFT) {
            return Failure(SettlementErrors.SETTLEMENT_STATEMENT_INVALID_STATE)
        }
        val expected = Price.sumOf(_lines.map { it.netAmount })
        if (_payableAmount != expected) {
            return Failure(SettlementErrors.SETTLEMENT_AMOUNT_MISMATCH)
        }
        _status = SettlementStatementStatus.CONFIRMED
        _confirmedAt = Instant.now()
        return Success(Unit)
    }

    override fun markPaid(paidAt: Instant): Result<Unit, BusinessError> {
        if (_status != SettlementStatementStatus.CONFIRMED) {
            return Failure(SettlementErrors.SETTLEMENT_STATEMENT_INVALID_STATE)
        }
        _status = SettlementStatementStatus.PAID
        _paidAt = paidAt
        publishEvent(
            SettlementPaidEvent(
                settlementId = id,
                statementNo = statementNo,
                merchantId = merchantId,
                payableAmount = payableAmount,
                paidAt = paidAt,
            )
        )
        return Success(Unit)
    }
}
