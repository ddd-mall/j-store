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
import com.jstore.contracts.commerce.*
import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessageHandler

abstract class TradeProcessMessageHandler<T : IntegrationMessage>(private val handlerName: String) :
    IntegrationMessageHandler<T> {
    final override fun handlerId(): String = handlerName

    protected fun com.jstore.common.utils.Result<Boolean, com.jstore.common.errors.BusinessError>
        .enforce() {
        getOrThrow(::BusinessErrorException)
    }
}

class StartTradeProcessHandler(private val trades: TradeProcessUseCase) :
    TradeProcessMessageHandler<StartTradeProcessCommand>("trade.start.v1") {
    override fun handle(message: StartTradeProcessCommand) = trades.start(message).enforce()
}

class SaleAuthorizedTradeHandler(private val trades: TradeProcessUseCase) :
    TradeProcessMessageHandler<SaleAuthorizedIntegrationEvent>("trade.sale-authorized.v1") {
    override fun handle(message: SaleAuthorizedIntegrationEvent) =
        trades.recordSaleAuthorized(message).enforce()
}

class SaleAuthorizationFailedTradeHandler(private val trades: TradeProcessUseCase) :
    TradeProcessMessageHandler<SaleAuthorizationFailedIntegrationEvent>(
        "trade.sale-authorization-failed.v1"
    ) {
    override fun handle(message: SaleAuthorizationFailedIntegrationEvent) =
        trades.recordSaleAuthorizationFailed(message).enforce()
}

class InventoryReservedTradeHandler(private val trades: TradeProcessUseCase) :
    TradeProcessMessageHandler<InventoryReservedIntegrationEvent>("trade.inventory-reserved.v1") {
    override fun handle(message: InventoryReservedIntegrationEvent) =
        trades.recordInventoryReserved(message).enforce()
}

class InventoryReservationFailedTradeHandler(private val trades: TradeProcessUseCase) :
    TradeProcessMessageHandler<InventoryReservationFailedIntegrationEvent>(
        "trade.inventory-reservation-failed.v1"
    ) {
    override fun handle(message: InventoryReservationFailedIntegrationEvent) =
        trades.recordInventoryReservationFailed(message).enforce()
}

class OrderCancelledTradeHandler(private val trades: TradeProcessUseCase) :
    TradeProcessMessageHandler<OrderCancelledIntegrationEvent>("trade.order-cancelled.v1") {
    override fun handle(message: OrderCancelledIntegrationEvent) = trades.close(message).enforce()
}

class OrderPaidTradeHandler(private val trades: TradeProcessUseCase) :
    TradeProcessMessageHandler<OrderPaidIntegrationEvent>("trade.order-paid.v1") {
    override fun handle(message: OrderPaidIntegrationEvent) = trades.markPaid(message).enforce()
}
