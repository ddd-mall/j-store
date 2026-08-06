package com.jstore.shop.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.framework.messaging.IntegrationMessageHandler
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.contracts.commerce.AuthorizeSaleCommand
import com.jstore.contracts.commerce.ReleaseSaleAuthorizationCommand
import com.jstore.shop.api.OfferSnapshotInfo
import com.jstore.shop.api.OfferSnapshotQueryService
import com.jstore.shop.domain.offer.MerchantId
import com.jstore.shop.domain.offer.OfferErrors
import com.jstore.shop.domain.offer.SaleAuthorization
import com.jstore.shop.domain.offer.SaleAuthorizationId
import com.jstore.shop.domain.offer.SaleAuthorizationRepository
import com.jstore.shop.domain.offer.SalesOfferGuard
import com.jstore.shop.domain.offer.SalesOfferId
import com.jstore.shop.domain.offer.SalesOfferRepository
import com.jstore.shop.domain.offer.StoreGuard
import com.jstore.shop.domain.offer.StoreId
import com.jstore.shop.domain.offer.StoreStatus
import com.jstore.shop.domain.offer.event.AuthorizedSaleLine
import com.jstore.shop.domain.offer.event.SaleAuthorizationRejectedEvent
import com.jstore.shop.domain.offer.event.SaleAuthorizedEvent
import java.time.Duration
import java.time.Instant

interface OfferAuthorizationUseCase {
    fun authorize(command: AuthorizeSaleCommand): Result<List<SaleAuthorization>, BusinessError>

    fun release(
        orderId: Long,
        authorizationIds: List<String>,
        now: Instant,
    ): Result<Unit, BusinessError>
}

class OfferAuthorizationService(
    private val storeGuard: StoreGuard,
    private val offerGuard: SalesOfferGuard,
    private val authorizationRepository: SaleAuthorizationRepository,
    private val publisher: DomainEventPublisher,
    private val ttl: Duration = Duration.ofMinutes(15),
) : OfferAuthorizationUseCase {
    override fun authorize(
        command: AuthorizeSaleCommand
    ): Result<List<SaleAuthorization>, BusinessError> {
        val existing = authorizationRepository.findByOrderId(command.orderId)
        if (existing.isNotEmpty()) return Success(existing)
        if (
            command.items.isEmpty() ||
                command.items.map { it.offerId }.distinct().size != command.items.size
        ) {
            return Failure(OfferErrors.ILLEGAL_STATE)
        }
        val ids = command.items.map { SalesOfferId(it.offerId) }.sortedBy { it.value }
        val storeIds = command.items.map { StoreId(it.storeId) }.distinct().sortedBy { it.value }
        val lockedStores = storeGuard.lock(storeIds).associateBy { it.id }
        if (
            lockedStores.size != storeIds.size ||
                lockedStores.values.any {
                    it.status != StoreStatus.ACTIVE ||
                        it.merchantId != MerchantId(command.merchantId)
                }
        ) {
            return Failure(OfferErrors.NOT_ACTIVE)
        }
        val locked = offerGuard.lock(ids).associateBy { it.id }
        if (locked.size != ids.size) return Failure(OfferErrors.NOT_FOUND)

        val authorizations =
            command.items
                .sortedBy { it.offerId }
                .map { item ->
                    val offer = locked.getValue(SalesOfferId(item.offerId))
                    if (
                        offer.merchantId != MerchantId(command.merchantId) ||
                            offer.skuId.value != item.skuId ||
                            offer.storeId.value != item.storeId
                    ) {
                        return Failure(OfferErrors.NOT_FOUND)
                    }
                    val result =
                        offer.authorize(
                            orderId = command.orderId,
                            quantity = item.quantity,
                            expectedPriceFen = item.unitPriceFen,
                            now = command.occurredAt,
                            expectedVersion = item.offerVersion,
                            ttl = ttl,
                        )
                    when (result) {
                        is Failure -> return result
                        is Success -> result.value
                    }
                }
        authorizations.forEach {
            authorizationRepository.save(it)
        }
        publisher.publishEvent(
            SaleAuthorizedEvent(
                command.orderId,
                authorizations.map {
                    AuthorizedSaleLine(
                        it.id.value,
                        it.offerId.value,
                        it.skuId.value,
                        it.quantity,
                        it.fulfillmentPolicy.preferredNodeId.value,
                        it.expiresAt,
                    )
                },
                command.occurredAt,
            )
        )
        return Success(authorizations)
    }

    override fun release(
        orderId: Long,
        authorizationIds: List<String>,
        now: Instant,
    ): Result<Unit, BusinessError> {
        for (rawId in authorizationIds.distinct()) {
            val authorization =
                authorizationRepository.findById(SaleAuthorizationId(rawId))
                    ?: return Failure(OfferErrors.AUTHORIZATION_NOT_FOUND)
            if (authorization.orderId != orderId)
                return Failure(OfferErrors.AUTHORIZATION_NOT_FOUND)
            authorization.release(now).onFailure {
                return Failure(it)
            }
            authorizationRepository.save(authorization)
            authorization.publishPendingEvents(publisher)
        }
        return Success(Unit)
    }
}

class AuthorizeSaleCommandHandler(
    private val useCase: OfferAuthorizationUseCase,
    private val publisher: DomainEventPublisher,
) : IntegrationMessageHandler<AuthorizeSaleCommand> {
    override fun handlerId() = "store.authorize-sale.v1"

    override fun handle(message: AuthorizeSaleCommand) {
        when (val result = useCase.authorize(message)) {
            is Success -> Unit
            is Failure ->
                publisher.publishEvent(
                    SaleAuthorizationRejectedEvent(
                        message.orderId,
                        result.error.message,
                        message.occurredAt,
                    )
                )
        }
    }
}

class ReleaseSaleAuthorizationCommandHandler(private val useCase: OfferAuthorizationUseCase) :
    IntegrationMessageHandler<ReleaseSaleAuthorizationCommand> {
    override fun handlerId() = "store.release-sale-authorization.v1"

    override fun handle(message: ReleaseSaleAuthorizationCommand) {
        useCase.release(message.orderId, message.authorizationIds, message.occurredAt).onFailure {
            throw IllegalStateException(it.message)
        }
    }
}

class OfferSnapshotQueryServiceImpl(private val offers: SalesOfferRepository) :
    OfferSnapshotQueryService {
    override fun queryOffers(offerIds: List<Long>): List<OfferSnapshotInfo> =
        offers.findAllByIds(offerIds.distinct().map(::SalesOfferId)).map {
            OfferSnapshotInfo(
                offerId = it.id.value,
                storeId = it.storeId.value,
                merchantId = it.merchantId.value,
                skuId = it.skuId.value,
                channelId = it.channel.channelId,
                market = it.channel.market,
                price = it.price,
                offerVersion = it.version,
                fulfillmentNodeId = it.fulfillmentPolicy.preferredNodeId.value,
                allowBackorder = it.fulfillmentPolicy.allowBackorder,
                active = it.status.name == "ACTIVE",
                startsAt = it.effectivePeriod.startsAt,
                endsAt = it.effectivePeriod.endsAt,
            )
        }
}
