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
package com.jstore.goods.domain.inventory

import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.math.BigDecimal
import java.time.LocalDateTime

data class ReservationRecord(
    override val id: ReservationId,
    val bizCode: String,
    val commodityCode: CommodityCode,
    val amount: BigDecimal,
    var status: ReservationStatus,
    val expiryTime: LocalDateTime,
) : Entity<ReservationId> {

    /** 确认扣减：RESERVED → CONFIRMED */
    fun confirm(): Result<Unit, BusinessError> {
        if (status == ReservationStatus.CONFIRMED) return Success(Unit)
        if (status == ReservationStatus.RELEASED || expiryTime < LocalDateTime.now()) {
            return Failure(CommonBusinessError.ILLEGAL_STATE.msg("预扣记录已释放或已过期"))
        }
        status = ReservationStatus.CONFIRMED
        return Success(Unit)
    }

    /** 释放预扣：RESERVED → RELEASED */
    fun release(): Result<Unit, BusinessError> {
        if (status == ReservationStatus.RELEASED) return Success(Unit)
        if (status == ReservationStatus.CONFIRMED) {
            return Failure(CommonBusinessError.ILLEGAL_STATE.msg("预扣记录已确认扣减，无法释放"))
        }
        status = ReservationStatus.RELEASED
        return Success(Unit)
    }
}

data class ReservationId(override val value: Long) : Id<Long>(value)

enum class ReservationStatus {
    RESERVED,
    CONFIRMED,
    RELEASED,
}
