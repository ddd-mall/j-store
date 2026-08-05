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
import com.jstore.common.framework.messaging.IntegrationMessageHandler
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.OrderCompletedIntegrationEvent
import com.jstore.contracts.commerce.PaymentCapturedIntegrationEvent
import com.jstore.contracts.commerce.PaymentRefundSucceededIntegrationEvent
import java.time.LocalDate
import java.time.ZoneOffset

class PaymentCapturedAccountingEventHandler(
    private val accountingOrderService: AccountingOrderService,
    private val accountingApplicationService: AccountingApplicationService,
) : IntegrationMessageHandler<PaymentCapturedIntegrationEvent> {
    override fun handlerId(): String = "accounting.record-payment-captured.v2"

    override fun handle(event: PaymentCapturedIntegrationEvent) {
        val info =
            when (
                val result = accountingOrderService.getOrderAccountingInfo(event.orderId.toString())
            ) {
                is Success -> result.value
                is Failure -> return
            }
        accountingApplicationService.recordOrderPaid(
            RecordOrderPaidCMD(
                orderId = info.orderId,
                merchantId = info.merchantId,
                paidAmount = Price.ofFen(event.amountFen),
                accountingDate = LocalDate.ofInstant(event.occurredAt, ZoneOffset.UTC),
                sourceDocument =
                    SourceDocument(SourceDocumentType.ORDER, info.orderId, "PaymentCapturedEvent"),
            )
        )
    }
}

class OrderCompletedAccountingEventHandler(
    private val accountingOrderService: AccountingOrderService,
    private val accountingApplicationService: AccountingApplicationService,
) : IntegrationMessageHandler<OrderCompletedIntegrationEvent> {
    override fun handlerId(): String = "accounting.record-order-completed.v2"

    override fun handle(event: OrderCompletedIntegrationEvent) {
        val info =
            when (
                val result = accountingOrderService.getOrderAccountingInfo(event.orderId.toString())
            ) {
                is Success -> result.value
                is Failure -> return
            }
        accountingApplicationService.recordOrderCompleted(
            RecordOrderCompletedCMD(
                orderId = info.orderId,
                merchantId = info.merchantId,
                commissionAmount = info.commissionAmount,
                accountingDate = LocalDate.ofInstant(event.occurredAt, ZoneOffset.UTC),
                sourceDocument =
                    SourceDocument(SourceDocumentType.ORDER, info.orderId, "OrderCompletedEvent"),
            )
        )
    }
}

class PaymentRefundSucceededAccountingEventHandler(
    private val accountingOrderService: AccountingOrderService,
    private val accountingApplicationService: AccountingApplicationService,
) : IntegrationMessageHandler<PaymentRefundSucceededIntegrationEvent> {
    override fun handlerId(): String = "accounting.record-payment-refund-succeeded.v2"

    override fun handle(event: PaymentRefundSucceededIntegrationEvent) {
        val orderId = event.orderId.toString()
        val info =
            when (val result = accountingOrderService.getOrderAccountingInfo(orderId)) {
                is Success -> result.value
                is Failure -> return
            }
        val originalSource =
            when (val result = accountingOrderService.getRefundableOriginalSource(orderId)) {
                is Success -> result.value
                is Failure -> return
            }
        accountingApplicationService.recordOrderRefundApproved(
            RecordOrderRefundApprovedCMD(
                orderId = info.orderId,
                merchantId = info.merchantId,
                refundAmount = Price.ofFen(event.amountFen),
                accountingDate = LocalDate.ofInstant(event.occurredAt, ZoneOffset.UTC),
                sourceDocument =
                    SourceDocument(
                        SourceDocumentType.REFUND,
                        event.refundId.toString(),
                        "PaymentRefundSucceededEvent",
                    ),
                originalSourceDocument = originalSource,
            )
        )
    }
}

class SettlementPaidAccountingEventHandler(
    private val accountingApplicationService: AccountingApplicationService
) : DomainEventListener<SettlementPaidEvent> {
    override fun listenerId(): String = "accounting.record-settlement-paid"

    override fun onDomainEvent(event: SettlementPaidEvent) {
        accountingApplicationService.recordSettlementPaid(
            RecordSettlementPaidCMD(
                settlementId = event.settlementId.value.toString(),
                merchantId = event.merchantId,
                paidAmount = event.payableAmount,
                accountingDate = LocalDate.ofInstant(event.paidAt, ZoneOffset.UTC),
                sourceDocument =
                    SourceDocument(
                        SourceDocumentType.SETTLEMENT,
                        event.settlementId.value.toString(),
                        "SettlementPaidEvent",
                    ),
            )
        )
    }
}
