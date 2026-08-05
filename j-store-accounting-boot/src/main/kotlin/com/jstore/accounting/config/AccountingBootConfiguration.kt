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
