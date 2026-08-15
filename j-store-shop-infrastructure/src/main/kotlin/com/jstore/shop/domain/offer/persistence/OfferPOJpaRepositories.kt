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
package com.jstore.shop.domain.offer.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StorePOJpaRepository : JpaRepository<StorePO, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StorePO s where s.id in :ids order by s.id")
    fun findAllByIdForUpdate(@Param("ids") ids: List<Long>): List<StorePO>
}

interface SalesOfferPOJpaRepository : JpaRepository<SalesOfferPO, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOfferPO o where o.id in :ids order by o.id")
    fun findAllByIdForUpdate(@Param("ids") ids: List<Long>): List<SalesOfferPO>
}

interface SaleAuthorizationPOJpaRepository : JpaRepository<SaleAuthorizationPO, String> {
    fun findAllByOrderPlanIdOrderByOfferId(orderPlanId: Long): List<SaleAuthorizationPO>
}
