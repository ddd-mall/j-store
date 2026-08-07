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
import com.jstore.goods.acl.event.AfterSaleStockRestoreRequestedEvent
import com.jstore.goods.acl.event.ConfirmItem
import com.jstore.goods.acl.event.ReleaseItem
import com.jstore.goods.acl.event.ReservationItem
import com.jstore.goods.acl.event.StockConfirmRequestedEvent
import com.jstore.goods.acl.event.StockReleaseRequestedEvent
import com.jstore.goods.acl.event.StockReservationRequestedEvent
import com.jstore.goods.acl.event.StockRestoreItem
import com.jstore.order.domain.aftersale.event.AfterSaleRefundSucceededEvent
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository
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
class OrderCreatedToStockReservationTranslator(
    private val domainEventPublisher: DomainEventPublisher
) : DomainEventListener<OrderCreatedEvent> {
    override fun listenerId(): String = "translator.order-created.to-stock-reservation-requested"

    override fun onDomainEvent(event: OrderCreatedEvent) {
        domainEventPublisher.publishEvent(
            StockReservationRequestedEvent(
                orderId = event.orderId.value,
                items =
                    event.items.map { ReservationItem(skuId = it.skuId, quantity = it.quantity) },
            )
        )
    }
}

@Component
class OrderPaidToStockConfirmTranslator(private val domainEventPublisher: DomainEventPublisher) :
    DomainEventListener<OrderPaidEvent> {
    override fun listenerId(): String = "translator.order-paid.to-stock-confirm-requested"

    override fun onDomainEvent(event: OrderPaidEvent) {
        domainEventPublisher.publishEvent(
            StockConfirmRequestedEvent(
                orderId = event.orderId.value,
                items = event.items.map { ConfirmItem(skuId = it.skuId) },
            )
        )
    }
}

@Component
class OrderCancelledToStockReleaseTranslator(
    private val domainEventPublisher: DomainEventPublisher,
    private val orderRepository: OrderRepository,
) : DomainEventListener<OrderCancelledEvent> {
    override fun listenerId(): String = "translator.order-cancelled.to-stock-release-requested"

    override fun onDomainEvent(event: OrderCancelledEvent) {
        val order = orderRepository.findById(OrderId(event.orderId.value)) ?: return
        domainEventPublisher.publishEvent(
            StockReleaseRequestedEvent(
                orderId = event.orderId.value,
                items = order.items.map { ReleaseItem(skuId = it.skuId) },
            )
        )
    }
}

@Component
class AfterSaleRefundSucceededToStockRestoreTranslator(
    private val domainEventPublisher: DomainEventPublisher
) : DomainEventListener<AfterSaleRefundSucceededEvent> {
    override fun listenerId(): String =
        "translator.after-sale-refund-succeeded.to-stock-restore-requested.v1"

    override fun onDomainEvent(event: AfterSaleRefundSucceededEvent) {
        val restoreItems =
            event.items.map { StockRestoreItem(skuId = it.skuId, quantity = it.quantity) }
        domainEventPublisher.publishEvent(
            AfterSaleStockRestoreRequestedEvent(
                afterSaleId = event.afterSaleId.value,
                orderId = event.orderId.value,
                items = restoreItems,
            )
        )
    }
}
