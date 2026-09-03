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
package com.jstore.order.service

import com.jstore.common.errors.BusinessErrorException
import com.jstore.common.geo.AddressComponent
import com.jstore.common.geo.CountryCode
import com.jstore.common.geo.DivisionLevel
import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.common.utils.getOrThrow
import com.jstore.contracts.commerce.CancelOrderFromTradeIntegrationCommand
import com.jstore.contracts.commerce.CreateOrderFromTradeIntegrationCommand
import com.jstore.contracts.commerce.OrderCreatedFromTradeIntegrationEvent
import com.jstore.contracts.commerce.OrderCreationRejectedFromTradeIntegrationEvent
import com.jstore.messaging.IntegrationMessageHandler
import com.jstore.messaging.IntegrationMessagePublisher
import java.time.Instant
import java.util.Locale

class CreateOrderFromTradeIntegrationCommandHandler(
    private val orders: InternalOrderCreationUseCase,
    private val publisher: IntegrationMessagePublisher,
    private val now: () -> Instant = Instant::now,
) : IntegrationMessageHandler<CreateOrderFromTradeIntegrationCommand> {
    override fun handlerId() = "order.create-from-trade.v2"

    override fun handle(message: CreateOrderFromTradeIntegrationCommand) {
        when (val result = orders.createOrder(message.toApplicationCommand())) {
            is Success ->
                publisher.publish(
                    OrderCreatedFromTradeIntegrationEvent(
                        message.tradeId,
                        message.orderPlanId,
                        result.value.id.value,
                        message.messageId,
                        now(),
                    )
                )
            is Failure ->
                publisher.publish(
                    OrderCreationRejectedFromTradeIntegrationEvent(
                        message.tradeId,
                        message.orderPlanId,
                        result.error.message,
                        message.messageId,
                        now(),
                    )
                )
        }
    }

    private fun CreateOrderFromTradeIntegrationCommand.toApplicationCommand() =
        CreateOrderFromTradeCommand(
            tradeId,
            orderPlanId,
            planDigest,
            merchantId,
            buyer.authenticationDomain,
            buyer.accountId,
            buyerName,
            buyerPhone,
            recipient.name,
            recipient.phone,
            recipient.email,
            I18nGeoAddress(
                CountryCode(shippingAddress.countryCode),
                shippingAddress.components.map { component ->
                    val names =
                        component.names.mapKeys { (languageTag, _) ->
                            Locale.forLanguageTag(languageTag)
                        }
                    AddressComponent(
                        component.code,
                        DivisionLevel(component.levelDepth, component.levelName),
                        names,
                        Locale.forLanguageTag(component.defaultLocale),
                    )
                },
            ),
            recipient.detailAddress.orEmpty(),
            recipient.postalCode,
            recipient.customsFields,
            items.map {
                CreateOrderFromTradeItem(
                    it.spuId,
                    it.skuId,
                    it.offerId,
                    it.storeId,
                    it.offerVersion,
                    it.fulfillmentNodeId,
                    it.channelId,
                    it.goodsName,
                    it.skuDescription,
                    it.quantity,
                    Price.ofFen(it.unitPriceFen),
                    it.catalogSnapshotVersion,
                )
            },
            Price.ofFen(payableAmountFen),
            currency,
        )
}

class CancelOrderFromTradeIntegrationCommandHandler(
    private val orders: InternalOrderCreationUseCase
) : IntegrationMessageHandler<CancelOrderFromTradeIntegrationCommand> {
    override fun handlerId() = "order.cancel-from-trade.v1"

    override fun handle(message: CancelOrderFromTradeIntegrationCommand) {
        orders
            .cancelOrder(message.tradeId, message.orderPlanId, message.reason)
            .getOrThrow(::BusinessErrorException)
    }
}
