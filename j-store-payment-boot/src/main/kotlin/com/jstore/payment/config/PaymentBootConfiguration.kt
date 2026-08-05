package com.jstore.payment.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.payment.domain.payment.PaymentOrderRepository
import com.jstore.payment.service.CreatePaymentForOrderCommandHandler
import com.jstore.payment.service.PaymentApplicationService
import com.jstore.payment.service.PaymentUseCase
import com.jstore.payment.service.RequestPaymentRefundCommandHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class PaymentBootConfiguration {
    @Bean
    fun paymentApplicationService(
        repository: PaymentOrderRepository,
        sequence: SnowFlakSequence,
        publisher: DomainEventPublisher,
    ) = PaymentApplicationService(repository, sequence, publisher)

    @Bean
    @Primary
    fun transactionalPaymentUseCase(
        paymentApplicationService: PaymentApplicationService,
        transactionManager: PlatformTransactionManager,
    ): PaymentUseCase = TransactionalPaymentUseCase(paymentApplicationService, transactionManager)

    @Bean
    fun createPaymentForOrderCommandHandler(service: PaymentUseCase) =
        CreatePaymentForOrderCommandHandler(service)

    @Bean
    fun requestPaymentRefundCommandHandler(service: PaymentUseCase) =
        RequestPaymentRefundCommandHandler(service)
}
