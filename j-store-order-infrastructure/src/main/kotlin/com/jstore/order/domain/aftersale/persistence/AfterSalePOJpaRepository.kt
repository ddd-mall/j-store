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
package com.jstore.order.domain.aftersale.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AfterSalePOJpaRepository : JpaRepository<AfterSalePO, Long> {
    fun findByOrderIdOrderByCreateTimeDesc(orderId: Long): List<AfterSalePO>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AfterSalePO a where a.id=:id")
    fun findByIdForUpdate(@Param("id") id: Long): AfterSalePO?
}

interface AfterSaleCapacityPOJpaRepository : JpaRepository<AfterSaleCapacityPO, Long> {
    @Modifying
    @Query(
        value =
            "insert into after_sale_capacities(order_item_id,order_id,quantity_ceiling,amount_ceiling,requested_quantity,requested_amount,approved_quantity,approved_amount,version) values (:itemId,:orderId,:quantity,:amount,0,0,0,0,0) on conflict (order_item_id) do nothing",
        nativeQuery = true,
    )
    fun initialize(
        @Param("itemId") itemId: Long,
        @Param("orderId") orderId: Long,
        @Param("quantity") quantity: Int,
        @Param("amount") amount: java.math.BigDecimal,
    ): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AfterSaleCapacityPO c where c.orderItemId in :ids order by c.orderItemId")
    fun lockAll(@Param("ids") ids: Collection<Long>): List<AfterSaleCapacityPO>
}

interface AfterSaleCommandReceiptPOJpaRepository : JpaRepository<AfterSaleCommandReceiptPO, Long> {
    fun findByActorIdAndCommandTypeAndIdempotencyKey(
        actorId: Long,
        commandType: String,
        idempotencyKey: String,
    ): AfterSaleCommandReceiptPO?
}
