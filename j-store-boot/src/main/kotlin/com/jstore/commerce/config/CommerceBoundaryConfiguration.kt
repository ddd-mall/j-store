package com.jstore.commerce.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.fulfillment.domain.FulfillmentOrderRepository
import com.jstore.fulfillment.service.CreateFulfillmentForOrderCommandHandler
import com.jstore.fulfillment.service.FulfillmentApplicationService
import com.jstore.payment.domain.payment.PaymentOrderRepository
import com.jstore.payment.service.CreatePaymentForOrderCommandHandler
import com.jstore.payment.service.PaymentApplicationService
import com.jstore.payment.service.RequestPaymentRefundCommandHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CommerceBoundaryConfiguration {
    @Bean
    fun paymentApplicationService(
        repository: PaymentOrderRepository,
        sequence: SnowFlakSequence,
        publisher: DomainEventPublisher,
    ) = PaymentApplicationService(repository, sequence, publisher)

    @Bean
    fun fulfillmentApplicationService(
        repository: FulfillmentOrderRepository,
        sequence: SnowFlakSequence,
        publisher: DomainEventPublisher,
    ) = FulfillmentApplicationService(repository, sequence, publisher)

    @Bean
    fun createPaymentForOrderCommandHandler(service: PaymentApplicationService) =
        CreatePaymentForOrderCommandHandler(service)

    @Bean
    fun requestPaymentRefundCommandHandler(service: PaymentApplicationService) =
        RequestPaymentRefundCommandHandler(service)

    @Bean
    fun createFulfillmentForOrderCommandHandler(service: FulfillmentApplicationService) =
        CreateFulfillmentForOrderCommandHandler(service)
}
