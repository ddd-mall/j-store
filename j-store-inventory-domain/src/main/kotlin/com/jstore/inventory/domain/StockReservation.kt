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
package com.jstore.inventory.domain

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.EventRecordingAggregateRoot
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.inventory.domain.event.StockReservationReleasedEvent
import java.time.Instant

class StockReservation(
    override val id: StockReservationId,
    val businessKey: String,
    val orderId: Long,
    val saleAuthorizationId: String,
    val skuId: SkuId,
    val fulfillmentNodeId: FulfillmentNodeId,
    val quantity: Int,
    val expiresAt: Instant,
    status: StockReservationStatus = StockReservationStatus.RESERVED,
    val persistenceVersion: Long = 0,
) : EventRecordingAggregateRoot<StockReservationId>() {
    private var _status = status

    val status: StockReservationStatus
        get() = _status

    init {
        require(
            businessKey.isNotBlank() &&
                orderId > 0 &&
                saleAuthorizationId.isNotBlank() &&
                quantity > 0
        )
    }

    fun confirm(): Result<Boolean, BusinessError> {
        if (_status == StockReservationStatus.CONFIRMED) return Success(false)
        if (_status != StockReservationStatus.RESERVED) {
            return Failure(InventoryErrors.ILLEGAL_RESERVATION_STATE)
        }
        _status = StockReservationStatus.CONFIRMED
        return Success(true)
    }

    fun release(now: Instant = Instant.now()): Result<Boolean, BusinessError> {
        if (_status == StockReservationStatus.RELEASED) return Success(false)
        if (_status != StockReservationStatus.RESERVED) {
            return Failure(InventoryErrors.ILLEGAL_RESERVATION_STATE)
        }
        _status = StockReservationStatus.RELEASED
        raise(StockReservationReleasedEvent(id, orderId, now))
        return Success(true)
    }
}
