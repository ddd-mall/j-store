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
import com.jstore.contracts.commerce.*
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.event.OrderCancellationRequestedEvent
import com.jstore.order.domain.order.event.OrderPaidEvent
import org.springframework.stereotype.Component

/**
 * 事件翻译器：订单领域事件 → 库存 ACL 集成事件
 *
 * 职责：纯格式转换，不包含任何业务逻辑 位于 boot 组装层，是两个限界上下文之间的桥梁
 */
@Component
class OrderCancelledToTradeTranslator(
    private val integrationMessagePublisher: IntegrationMessagePublisher
) : DomainEventListener<OrderCancellationRequestedEvent> {
    override fun listenerId(): String = "translator.order-cancelled.to-trade.v1"

    override fun onDomainEvent(event: OrderCancellationRequestedEvent) {
        integrationMessagePublisher.publish(
            OrderCancelledIntegrationEvent(
                tradeId = event.tradeId,
                orderPlanId = event.orderPlanId,
                orderId = event.orderId.value,
                reason = event.reason,
                sourceMessageId = event.eventId,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}

@Component
class OrderPaidToStockConfirmTranslator(
    private val orders: OrderRepository,
    private val integrationMessagePublisher: IntegrationMessagePublisher,
) : DomainEventListener<OrderPaidEvent> {
    override fun listenerId(): String = "translator.order-paid.to-stock-confirm-requested.v2"

    override fun onDomainEvent(event: OrderPaidEvent) {
        val order = requireNotNull(orders.findById(event.orderId))
        val tradeId = order.sourceTradeId ?: return
        val orderPlanId = order.sourceOrderPlanId ?: return
        integrationMessagePublisher.publish(
            ConfirmInventoryCommand(
                tradeId = tradeId,
                orderPlanId = orderPlanId,
                items = event.items.map { ContractItem(skuId = it.skuId, quantity = it.quantity) },
                sourceMessageId = event.eventId,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}
