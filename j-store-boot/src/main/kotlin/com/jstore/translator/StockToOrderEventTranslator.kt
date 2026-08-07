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
package com.jstore.translator

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.goods.domain.inventory.event.StockReservationFailedEvent
import com.jstore.goods.domain.inventory.event.StockReservedEvent
import com.jstore.order.acl.event.OrderStockConfirmedEvent
import com.jstore.order.acl.event.OrderStockInsufficientEvent
import org.springframework.stereotype.Component

/**
 * 事件翻译器：库存领域事件 → 订单 ACL 集成事件
 *
 * 职责：纯格式转换，不包含任何业务逻辑
 */
@Component
class StockReservedToOrderConfirmedTranslator(
    private val domainEventPublisher: DomainEventPublisher
) : DomainEventListener<StockReservedEvent> {
    override fun listenerId(): String = "translator.stock-reserved.to-order-stock-confirmed"

    override fun onDomainEvent(event: StockReservedEvent) {
        domainEventPublisher.publishEvent(OrderStockConfirmedEvent(orderId = event.orderId))
    }
}

@Component
class StockReservationFailedToOrderInsufficientTranslator(
    private val domainEventPublisher: DomainEventPublisher
) : DomainEventListener<StockReservationFailedEvent> {
    override fun listenerId(): String =
        "translator.stock-reservation-failed.to-order-stock-insufficient"

    override fun onDomainEvent(event: StockReservationFailedEvent) {
        domainEventPublisher.publishEvent(
            OrderStockInsufficientEvent(orderId = event.orderId, reason = event.reason)
        )
    }
}
