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
package com.jstore.shop.domain.merchant

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.LocalDateTime

class Merchant(
    override val id: MerchantId,
    name: String,
    status: MerchantStatus = MerchantStatus.ACTIVE,
    val createTime: LocalDateTime = LocalDateTime.now(),
    updateTime: LocalDateTime = LocalDateTime.now(),
) : AggregateRoot<MerchantId> {

    var name: String = name.trim()
        private set

    var status: MerchantStatus = status
        private set

    var updateTime: LocalDateTime = updateTime
        private set

    init {
        require(validName(this.name)) { "merchant name must contain 1 to 128 characters" }
    }

    fun rename(newName: String): Result<Unit, BusinessError> {
        val normalized = newName.trim()
        if (!validName(normalized)) return Failure(MerchantErrors.NAME_INVALID)
        name = normalized
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    fun disable(): Result<Unit, BusinessError> {
        if (status != MerchantStatus.ACTIVE) return Failure(MerchantErrors.ILLEGAL_STATE)
        status = MerchantStatus.DISABLED
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    fun enable(): Result<Unit, BusinessError> {
        if (status != MerchantStatus.DISABLED) return Failure(MerchantErrors.ILLEGAL_STATE)
        status = MerchantStatus.ACTIVE
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    companion object {
        fun validName(name: String): Boolean = name.isNotBlank() && name.length <= 128
    }
}
