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
package com.jstore.order.domain.order

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Price

/** 订单行项实体接口 生命周期依附于 Order 聚合根，通过 ID 引用商品（跨聚合引用规则） */
interface OrderItem : Entity<OrderItemId> {
    val offerId: Long
    val storeId: Long
    val offerVersion: Long
    val fulfillmentNodeId: String
    val channelId: String
    val skuId: Long
    val spuId: Long
    val goodsName: String
    val skuDescription: String
    val quantity: Int
    val unitPrice: Price
    val snapshotVersion: Long
    val status: OrderItemStatus

    val purchasedAmount: Price
    val refundedQuantity: Int
    val refundedAmount: Price
    val refundableQuantity: Int
    val refundableAmount: Price

    /** 计算行项小计 */
    fun subtotal(): Price
}
