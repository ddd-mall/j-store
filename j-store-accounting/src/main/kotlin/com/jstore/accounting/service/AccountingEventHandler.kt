/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.jstore.payment.domain.payment.event.PaymentCapturedEvent
import com.jstore.payment.domain.payment.event.PaymentRefundSucceededEvent
import java.time.LocalDate
import java.time.ZoneOffset

class PaymentCapturedAccountingEventHandler(
    private val accountingOrderService: AccountingOrderService,
    private val accountingApplicationService: AccountingApplicationService,
) : DomainEventListener<PaymentCapturedEvent> {
    override fun listenerId(): String = "accounting.record-payment-captured.v1"

    override fun onDomainEvent(event: PaymentCapturedEvent) {
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
                paidAmount = event.amount,
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
) : DomainEventListener<OrderCompletedEvent> {
    override fun listenerId(): String = "accounting.record-order-completed"

    override fun onDomainEvent(event: OrderCompletedEvent) {
        val info =
            when (
                val result =
                    accountingOrderService.getOrderAccountingInfo(event.orderId.value.toString())
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
) : DomainEventListener<PaymentRefundSucceededEvent> {
    override fun listenerId(): String = "accounting.record-payment-refund-succeeded.v1"

    override fun onDomainEvent(event: PaymentRefundSucceededEvent) {
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
                refundAmount = event.amount,
                accountingDate = LocalDate.ofInstant(event.occurredAt, ZoneOffset.UTC),
                sourceDocument =
                    SourceDocument(
                        SourceDocumentType.REFUND,
                        event.refundId.value.toString(),
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
