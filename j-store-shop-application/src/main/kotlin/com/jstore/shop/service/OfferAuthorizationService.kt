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
package com.jstore.shop.service

import com.jstore.common.currency.CurrencyCode
import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.contracts.commerce.AuthorizeSaleCommand
import com.jstore.contracts.commerce.ReleaseSaleAuthorizationCommand
import com.jstore.messaging.IntegrationMessageHandler
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
import com.jstore.shop.domain.offer.StoreRepository
import com.jstore.shop.domain.offer.StoreStatus
import com.jstore.shop.domain.offer.event.AuthorizedSaleLine
import com.jstore.shop.domain.offer.event.SaleAuthorizationRejectedEvent
import com.jstore.shop.domain.offer.event.SaleAuthorizedEvent
import java.time.Duration
import java.time.Instant

interface OfferAuthorizationUseCase {
    fun authorize(command: AuthorizeSaleCommand): Result<List<SaleAuthorization>, BusinessError>

    fun release(
        tradeId: Long,
        orderPlanId: Long,
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
        val existing = authorizationRepository.findByOrderPlanId(command.orderPlanId)
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
                            tradeId = command.tradeId,
                            orderPlanId = command.orderPlanId,
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
                command.tradeId,
                command.orderPlanId,
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
        tradeId: Long,
        orderPlanId: Long,
        authorizationIds: List<String>,
        now: Instant,
    ): Result<Unit, BusinessError> {
        for (rawId in authorizationIds.distinct()) {
            val authorization =
                authorizationRepository.findById(SaleAuthorizationId(rawId))
                    ?: return Failure(OfferErrors.AUTHORIZATION_NOT_FOUND)
            if (authorization.tradeId != tradeId || authorization.orderPlanId != orderPlanId)
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
                        message.tradeId,
                        message.orderPlanId,
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
        useCase
            .release(
                message.tradeId,
                message.orderPlanId,
                message.authorizationIds,
                message.occurredAt,
            )
            .onFailure {
                throw IllegalStateException(it.message)
            }
    }
}

class OfferSnapshotQueryServiceImpl(
    private val offers: SalesOfferRepository,
    private val stores: StoreRepository,
    private val defaultCurrency: String,
    private val now: () -> Instant = Instant::now,
) : OfferSnapshotQueryService {
    init {
        require(CurrencyCode.isValid(defaultCurrency))
    }

    override fun queryOffers(offerIds: List<Long>): List<OfferSnapshotInfo> {
        val instant = now()
        return offers.findAllByIds(offerIds.distinct().map(::SalesOfferId)).map {
            val store = stores.findById(it.storeId)
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
                storeActive = store?.status == StoreStatus.ACTIVE,
                effectiveNow = it.effectivePeriod.contains(instant),
                currency = defaultCurrency,
            )
        }
    }
}
