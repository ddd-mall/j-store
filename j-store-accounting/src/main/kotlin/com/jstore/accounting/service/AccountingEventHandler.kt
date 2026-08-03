package com.jstore.accounting.service

import com.jstore.accounting.acl.AccountingOrderService
import com.jstore.accounting.domain.journal.SourceDocument
import com.jstore.accounting.domain.journal.SourceDocumentType
import com.jstore.accounting.domain.settlement.event.SettlementPaidEvent
import com.jstore.accounting.service.command.RecordOrderCompletedCMD
import com.jstore.accounting.service.command.RecordOrderPaidCMD
import com.jstore.accounting.service.command.RecordOrderRefundApprovedCMD
import com.jstore.accounting.service.command.RecordSettlementPaidCMD
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.event.OrderCompletedEvent
import com.jstore.order.domain.order.event.OrderPaidEvent
import com.jstore.order.domain.aftersale.event.AfterSaleApprovedEvent
import com.jstore.common.properties.Price
import java.time.LocalDate
import java.time.ZoneOffset

class OrderPaidAccountingEventHandler(
    private val accountingOrderService: AccountingOrderService,
    private val accountingApplicationService: AccountingApplicationService,
) : DomainEventListener<OrderPaidEvent> {
    override fun listenerId(): String = "accounting.record-order-paid"

    override fun onDomainEvent(event: OrderPaidEvent) {
        val info = when (val result = accountingOrderService.getOrderAccountingInfo(event.orderId.value.toString())) {
            is Success -> result.value
            is Failure -> return
        }
        accountingApplicationService.recordOrderPaid(
            RecordOrderPaidCMD(
                orderId = info.orderId,
                merchantId = info.merchantId,
                paidAmount = event.paidAmount,
                accountingDate = LocalDate.ofInstant(event.occurredAt, ZoneOffset.UTC),
                sourceDocument = SourceDocument(SourceDocumentType.ORDER, info.orderId, "OrderPaidEvent"),
            )
        )
    }
}

class OrderCompletedAccountingEventHandler(
    private val accountingOrderService: AccountingOrderService,
    private val accountingApplicationService: AccountingApplicationService,
) : DomainEventListener<OrderCompletedEvent> {
    override fun listenerId(): String = "accounting.record-order-completed"

    override fun onDomainEvent(event: OrderCompletedEvent) {
        val info = when (val result = accountingOrderService.getOrderAccountingInfo(event.orderId.value.toString())) {
            is Success -> result.value
            is Failure -> return
        }
        accountingApplicationService.recordOrderCompleted(
            RecordOrderCompletedCMD(
                orderId = info.orderId,
                merchantId = info.merchantId,
                commissionAmount = info.commissionAmount,
                accountingDate = LocalDate.ofInstant(event.occurredAt, ZoneOffset.UTC),
                sourceDocument = SourceDocument(SourceDocumentType.ORDER, info.orderId, "OrderCompletedEvent"),
            )
        )
    }
}

class OrderRefundApprovedAccountingEventHandler(
    private val accountingOrderService: AccountingOrderService,
    private val accountingApplicationService: AccountingApplicationService,
) : DomainEventListener<AfterSaleApprovedEvent> {
    override fun listenerId(): String = "accounting.record-after-sale-approved.v1"

    override fun onDomainEvent(event: AfterSaleApprovedEvent) {
        val orderId = event.orderId.value.toString()
        val info = when (val result = accountingOrderService.getOrderAccountingInfo(orderId)) {
            is Success -> result.value
            is Failure -> return
        }
        val originalSource = when (val result = accountingOrderService.getRefundableOriginalSource(orderId)) {
            is Success -> result.value
            is Failure -> return
        }
        accountingApplicationService.recordOrderRefundApproved(
            RecordOrderRefundApprovedCMD(
                orderId = info.orderId,
                merchantId = info.merchantId,
                refundAmount = Price.sumOf(event.items.map { it.amount }),
                accountingDate = LocalDate.ofInstant(event.occurredAt, ZoneOffset.UTC),
                sourceDocument = SourceDocument(SourceDocumentType.REFUND, event.afterSaleId.value.toString(), "AfterSaleApprovedEvent"),
                originalSourceDocument = originalSource,
            )
        )
    }
}

class SettlementPaidAccountingEventHandler(
    private val accountingApplicationService: AccountingApplicationService,
) : DomainEventListener<SettlementPaidEvent> {
    override fun listenerId(): String = "accounting.record-settlement-paid"

    override fun onDomainEvent(event: SettlementPaidEvent) {
        accountingApplicationService.recordSettlementPaid(
            RecordSettlementPaidCMD(
                settlementId = event.settlementId.value.toString(),
                merchantId = event.merchantId,
                paidAmount = event.payableAmount,
                accountingDate = LocalDate.ofInstant(event.paidAt, ZoneOffset.UTC),
                sourceDocument = SourceDocument(SourceDocumentType.SETTLEMENT, event.settlementId.value.toString(), "SettlementPaidEvent"),
            )
        )
    }
}
