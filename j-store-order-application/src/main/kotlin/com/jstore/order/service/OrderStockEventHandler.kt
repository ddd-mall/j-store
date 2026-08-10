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

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.onFailure
import com.jstore.contracts.commerce.InventoryReservedIntegrationEvent
import com.jstore.messaging.IntegrationMessageHandler
import com.jstore.order.domain.order.OrderId

/** 订单应用层事件处理器：监听库存确认成功事件，将订单转为待支付 */
class OrderStockConfirmedEventHandler(private val orderService: OrderUseCase) :
    IntegrationMessageHandler<InventoryReservedIntegrationEvent> {
    override fun handlerId(): String = "order.confirm-stock-on-stock-confirmed.v2"

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class)
    }

    override fun handle(message: InventoryReservedIntegrationEvent) {
        val event = message
        log.info("库存已确认，订单转为待支付: orderId=${event.orderId}")
        orderService.confirmStock(OrderId(event.orderId)).onFailure {
            log.error("确认订单库存失败: orderId=${event.orderId}, error=${it.message}")
        }
    }
}
