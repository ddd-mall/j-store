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
import com.jstore.order.domain.order.event.OrderCancelledEvent
import com.jstore.order.domain.order.event.OrderCreatedEvent
import com.jstore.order.domain.order.event.OrderPaidEvent
import org.springframework.stereotype.Component

/**
 * 事件翻译器：订单领域事件 → 库存 ACL 集成事件
 *
 * 职责：纯格式转换，不包含任何业务逻辑 位于 boot 组装层，是两个限界上下文之间的桥梁
 */
@Component
class OrderCreatedToTradeTranslator(
    private val integrationMessagePublisher: IntegrationMessagePublisher
) : DomainEventListener<OrderCreatedEvent> {
    override fun listenerId(): String = "translator.order-created.to-trade.v1"

    override fun onDomainEvent(event: OrderCreatedEvent) {
        integrationMessagePublisher.publish(
            StartTradeProcessCommand(
                orderId = event.orderId.value,
                items =
                    event.items.map {
                        ContractSaleItem(
                            offerId = it.offerId,
                            storeId = it.storeId,
                            spuId = it.spuId,
                            skuId = it.skuId,
                            quantity = it.quantity,
                            catalogSnapshotVersion = it.catalogSnapshotVersion,
                            offerVersion = it.offerVersion,
                            fulfillmentNodeId = it.fulfillmentNodeId,
                            channelId = it.channelId,
                            unitPriceFen = it.unitPrice.fen,
                        )
                    },
                sourceMessageId = event.eventId,
                merchantId = event.merchantId.value,
                payableAmountFen = event.payableAmount.fen,
                currency = event.currency,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}

@Component
class OrderPaidToStockConfirmTranslator(
    private val integrationMessagePublisher: IntegrationMessagePublisher
) : DomainEventListener<OrderPaidEvent> {
    override fun listenerId(): String = "translator.order-paid.to-stock-confirm-requested"

    override fun onDomainEvent(event: OrderPaidEvent) {
        integrationMessagePublisher.publish(
            ConfirmInventoryCommand(
                orderId = event.orderId.value,
                items = event.items.map { ContractItem(skuId = it.skuId, quantity = it.quantity) },
                sourceMessageId = event.eventId,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}

@Component
class OrderCancelledToTradeTranslator(
    private val integrationMessagePublisher: IntegrationMessagePublisher
) : DomainEventListener<OrderCancelledEvent> {
    override fun listenerId(): String = "translator.order-cancelled.to-trade.v1"

    override fun onDomainEvent(event: OrderCancelledEvent) {
        integrationMessagePublisher.publish(
            OrderCancelledIntegrationEvent(
                orderId = event.orderId.value,
                reason = event.reason,
                sourceMessageId = event.eventId,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}

@Component
class OrderPaidToTradeTranslator(
    private val integrationMessagePublisher: IntegrationMessagePublisher
) : DomainEventListener<OrderPaidEvent> {
    override fun listenerId(): String = "translator.order-paid.to-trade.v1"

    override fun onDomainEvent(event: OrderPaidEvent) {
        integrationMessagePublisher.publish(
            OrderPaidIntegrationEvent(
                orderId = event.orderId.value,
                sourceMessageId = event.eventId,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}
