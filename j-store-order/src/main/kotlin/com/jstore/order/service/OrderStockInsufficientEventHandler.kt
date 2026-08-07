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

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.onFailure
import com.jstore.order.acl.event.OrderStockInsufficientEvent
import com.jstore.order.domain.order.OrderId

/** 订单应用层事件处理器：监听库存不足事件，取消订单 */
class OrderStockInsufficientEventHandler(private val orderService: OrderService) :
    DomainEventListener<OrderStockInsufficientEvent> {
    override fun listenerId(): String = "order.cancel-on-stock-insufficient"

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class)
    }

    override fun onDomainEvent(event: OrderStockInsufficientEvent) {
        log.warn("库存不足，取消订单: orderId=${event.orderId}, reason=${event.reason}")
        orderService.markStockInsufficient(OrderId(event.orderId), event.reason).onFailure {
            log.error("取消订单失败: orderId=${event.orderId}, error=${it.message}")
        }
    }
}
