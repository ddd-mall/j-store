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

import com.jstore.common.framework.AggregateRepository
import com.jstore.common.query.Page

/**
 * 订单仓储接口 ✅ 改进：
 * - 移除基础设施概念（如findByIdAndLock）
 * - 清晰的方法语义（add vs save）
 * - 只定义业务相关方法
 */
interface OrderRepository : AggregateRepository<OrderId, Order> {

    /** 添加新订单 */
    fun add(order: Order)

    /** 保存已存在的订单（更新） */
    override fun save(entity: Order): Order

    /** 根据ID查询订单 */
    override fun findById(id: OrderId): Order?

    /** 根据买家ID查询订单列表 */
    fun findByBuyerUserId(uid: Long): List<Order>

    fun findBySourceOrderPlanId(orderPlanId: Long): Order? = null

    /** 分页查询用户订单 */
    fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order>
}
