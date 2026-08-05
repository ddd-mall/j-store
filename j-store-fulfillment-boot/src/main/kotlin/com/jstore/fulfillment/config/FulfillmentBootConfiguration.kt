package com.jstore.fulfillment.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.fulfillment.domain.FulfillmentOrderRepository
import com.jstore.fulfillment.service.CreateFulfillmentForOrderCommandHandler
import com.jstore.fulfillment.service.FulfillmentApplicationService
import com.jstore.fulfillment.service.FulfillmentUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class FulfillmentBootConfiguration {
    @Bean
    fun fulfillmentApplicationService(
        repository: FulfillmentOrderRepository,
        sequence: SnowFlakSequence,
        publisher: DomainEventPublisher,
    ) = FulfillmentApplicationService(repository, sequence, publisher)

    @Bean
    @Primary
    fun transactionalFulfillmentUseCase(
        fulfillmentApplicationService: FulfillmentApplicationService,
        transactionManager: PlatformTransactionManager,
    ): FulfillmentUseCase =
        TransactionalFulfillmentUseCase(fulfillmentApplicationService, transactionManager)

    @Bean
    fun createFulfillmentForOrderCommandHandler(service: FulfillmentUseCase) =
        CreateFulfillmentForOrderCommandHandler(service)
}
