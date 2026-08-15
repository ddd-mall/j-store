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
package com.jstore.trade.service

import com.jstore.common.errors.BusinessErrorException
import com.jstore.common.utils.getOrThrow
import com.jstore.contracts.commerce.InventoryReservationFailedIntegrationEvent
import com.jstore.contracts.commerce.InventoryReservedIntegrationEvent
import com.jstore.contracts.commerce.OrderCancelledIntegrationEvent
import com.jstore.contracts.commerce.SaleAuthorizationFailedIntegrationEvent
import com.jstore.contracts.commerce.SaleAuthorizedIntegrationEvent
import com.jstore.messaging.IntegrationMessageHandler

class TradePlanSaleAuthorizedHandler(private val trades: TradeSagaUseCase) :
    IntegrationMessageHandler<SaleAuthorizedIntegrationEvent> {
    override fun handlerId() = "trade.plan-sale-authorized.v2"

    override fun handle(message: SaleAuthorizedIntegrationEvent) {
        trades.recordSaleAuthorized(message).getOrThrow(::BusinessErrorException)
    }
}

class TradePlanInventoryReservedHandler(private val trades: TradeSagaUseCase) :
    IntegrationMessageHandler<InventoryReservedIntegrationEvent> {
    override fun handlerId() = "trade.plan-inventory-reserved.v2"

    override fun handle(message: InventoryReservedIntegrationEvent) {
        trades.recordInventoryReserved(message).getOrThrow(::BusinessErrorException)
    }
}

class TradePlanSaleAuthorizationFailedHandler(private val trades: TradeSagaUseCase) :
    IntegrationMessageHandler<SaleAuthorizationFailedIntegrationEvent> {
    override fun handlerId() = "trade.plan-sale-authorization-failed.v2"

    override fun handle(message: SaleAuthorizationFailedIntegrationEvent) {
        trades.recordSaleAuthorizationFailed(message).getOrThrow(::BusinessErrorException)
    }
}

class TradePlanInventoryReservationFailedHandler(private val trades: TradeSagaUseCase) :
    IntegrationMessageHandler<InventoryReservationFailedIntegrationEvent> {
    override fun handlerId() = "trade.plan-inventory-reservation-failed.v2"

    override fun handle(message: InventoryReservationFailedIntegrationEvent) {
        trades.recordInventoryReservationFailed(message).getOrThrow(::BusinessErrorException)
    }
}

class TradeOrderCancelledHandler(private val trades: TradeSagaUseCase) :
    IntegrationMessageHandler<OrderCancelledIntegrationEvent> {
    override fun handlerId() = "trade.order-cancelled.v2"

    override fun handle(message: OrderCancelledIntegrationEvent) {
        trades.recordOrderCancelled(message).getOrThrow(::BusinessErrorException)
    }
}
