package com.jstore.shop.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.messaging.IntegrationMessage
import com.jstore.common.framework.messaging.IntegrationMessageHandler
import com.jstore.contracts.commerce.AuthorizeSaleCommand
import com.jstore.contracts.commerce.ReleaseSaleAuthorizationCommand
import com.jstore.shop.api.OfferSnapshotQueryService
import com.jstore.shop.domain.offer.SaleAuthorizationRepository
import com.jstore.shop.domain.offer.SalesOfferGuard
import com.jstore.shop.domain.offer.SalesOfferRepository
import com.jstore.shop.domain.offer.StoreGuard
import com.jstore.shop.service.AuthorizeSaleCommandHandler
import com.jstore.shop.service.OfferAuthorizationService
import com.jstore.shop.service.OfferSnapshotQueryServiceImpl
import com.jstore.shop.service.ReleaseSaleAuthorizationCommandHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class OfferBootConfiguration {
    @Bean
    fun offerAuthorizationService(
        storeGuard: StoreGuard,
        guard: SalesOfferGuard,
        authorizations: SaleAuthorizationRepository,
        publisher: DomainEventPublisher,
    ) = OfferAuthorizationService(storeGuard, guard, authorizations, publisher)

    @Bean
    fun offerSnapshotQueryService(offers: SalesOfferRepository): OfferSnapshotQueryService =
        OfferSnapshotQueryServiceImpl(offers)

    @Bean
    fun authorizeSaleHandler(
        service: OfferAuthorizationService,
        publisher: DomainEventPublisher,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<AuthorizeSaleCommand> =
        transactional(AuthorizeSaleCommandHandler(service, publisher), transactionManager)

    @Bean
    fun releaseSaleAuthorizationHandler(
        service: OfferAuthorizationService,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<ReleaseSaleAuthorizationCommand> =
        transactional(ReleaseSaleAuthorizationCommandHandler(service), transactionManager)

    private fun <T : IntegrationMessage> transactional(
        delegate: IntegrationMessageHandler<T>,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<T> =
        object : IntegrationMessageHandler<T> {
            private val transaction = TransactionTemplate(transactionManager)

            override fun handlerId() = delegate.handlerId()

            override fun handle(message: T) {
                transaction.executeWithoutResult { delegate.handle(message) }
            }
        }
}
