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
package com.jstore.shop.domain.offer

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRepository
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success

enum class StoreStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED,
}

class Store(
    override val id: StoreId,
    val merchantId: MerchantId,
    val name: String,
    status: StoreStatus,
    val persistenceVersion: Long = 0,
) : AggregateRoot<StoreId> {
    private var _status = status

    val status: StoreStatus
        get() = _status

    init {
        require(name.isNotBlank())
    }

    fun suspend(): Result<Unit, BusinessError> {
        if (_status != StoreStatus.ACTIVE) return Failure(OfferErrors.ILLEGAL_STATE)
        _status = StoreStatus.SUSPENDED
        return Success(Unit)
    }

    fun activate(): Result<Unit, BusinessError> {
        if (_status != StoreStatus.SUSPENDED) return Failure(OfferErrors.ILLEGAL_STATE)
        _status = StoreStatus.ACTIVE
        return Success(Unit)
    }
}

interface StoreRepository : AggregateRepository<StoreId, Store>

fun interface StoreGuard {
    fun lock(ids: List<StoreId>): List<Store>
}
