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
import com.jstore.common.framework.Entity
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.LocalDateTime

class MerchantMembership(
    override val id: MerchantMembershipId,
    val merchantId: MerchantId,
    val userId: Long,
    roles: Set<MerchantRole>,
    status: MerchantMembershipStatus = MerchantMembershipStatus.ACTIVE,
    val createTime: LocalDateTime = LocalDateTime.now(),
    updateTime: LocalDateTime = LocalDateTime.now(),
) : Entity<MerchantMembershipId> {
    var roles: Set<MerchantRole> = roles.toSet()
        private set

    var status: MerchantMembershipStatus = status
        private set

    var updateTime: LocalDateTime = updateTime
        private set

    init {
        require(userId > 0) { "userId must be positive" }
        require(this.roles.isNotEmpty()) { "membership roles must not be empty" }
    }

    fun allows(permission: MerchantPermission): Boolean =
        status == MerchantMembershipStatus.ACTIVE && roles.any { permission in it.permissions }

    fun changeRoles(newRoles: Set<MerchantRole>): Result<Unit, BusinessError> {
        if (MerchantRole.OWNER in roles) return Failure(MerchantErrors.OWNER_PROTECTED)
        if (newRoles.isEmpty()) return Failure(MerchantErrors.ROLES_EMPTY)
        if (MerchantRole.OWNER in newRoles) return Failure(MerchantErrors.OWNER_ROLE_RESERVED)
        roles = newRoles.toSet()
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    fun disable(): Result<Unit, BusinessError> {
        if (MerchantRole.OWNER in roles) return Failure(MerchantErrors.OWNER_PROTECTED)
        if (status != MerchantMembershipStatus.ACTIVE) return Failure(MerchantErrors.ILLEGAL_STATE)
        status = MerchantMembershipStatus.DISABLED
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    fun enable(): Result<Unit, BusinessError> {
        if (status != MerchantMembershipStatus.DISABLED)
            return Failure(MerchantErrors.ILLEGAL_STATE)
        status = MerchantMembershipStatus.ACTIVE
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }
}
