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
package com.jstore.accounting.config

import com.jstore.accounting.acl.*
import com.jstore.accounting.domain.account.LedgerAccountRepository
import com.jstore.accounting.domain.journal.*
import com.jstore.accounting.service.*
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.utils.*
import com.jstore.order.domain.order.*
import java.time.ZoneOffset
import org.springframework.context.annotation.*
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class AccountingBootConfiguration {
    @Bean
    fun accountingApplicationService(
        j: JournalEntryRepository,
        l: LedgerAccountRepository,
        p: AccountingPeriodRepository,
    ) = AccountingApplicationService(j, l, p)

    @Bean
    @Primary
    fun transactionalAccountingUseCase(
        service: AccountingApplicationService,
        transactionManager: PlatformTransactionManager,
    ): AccountingUseCase = TransactionalAccountingUseCase(service, transactionManager)

    @Bean
    fun settlementApplicationService(
        repository: com.jstore.accounting.domain.settlement.SettlementStatementRepository,
        publisher: DomainEventPublisher,
    ) = SettlementApplicationService(repository, publisher)

    @Bean
    @Primary
    fun transactionalSettlementUseCase(
        service: SettlementApplicationService,
        transactionManager: PlatformTransactionManager,
    ): SettlementUseCase = TransactionalSettlementUseCase(service, transactionManager)

    @Bean
    fun accountingOrderService(orders: OrderRepository) =
        object : AccountingOrderService {
            override fun getOrderAccountingInfo(orderId: String) =
                orders.findById(OrderId(orderId.toLong()))?.let {
                    Success(
                        OrderAccountingInfo(
                            orderId,
                            it.merchantId.value.toString(),
                            it.paidAmount,
                            com.jstore.common.properties.Price.ZERO,
                            it.updateTime.toInstant(ZoneOffset.UTC),
                        )
                    )
                } ?: Failure(OrderErrors.ORDER_NOT_FOUND)

            override fun getRefundableOriginalSource(orderId: String) =
                Success(SourceDocument(SourceDocumentType.ORDER, orderId, "PaymentCapturedEvent"))
        }

    @Bean
    fun paymentCapturedAccountingEventHandler(
        a: AccountingOrderService,
        s: AccountingUseCase,
    ) = PaymentCapturedAccountingEventHandler(a, s)

    @Bean
    fun orderCompletedAccountingEventHandler(
        a: AccountingOrderService,
        s: AccountingUseCase,
    ) = OrderCompletedAccountingEventHandler(a, s)

    @Bean
    fun paymentRefundSucceededAccountingEventHandler(
        a: AccountingOrderService,
        s: AccountingUseCase,
    ) = PaymentRefundSucceededAccountingEventHandler(a, s)

    @Bean
    fun settlementPaidAccountingEventHandler(s: AccountingUseCase) =
        SettlementPaidAccountingEventHandler(s)
}
