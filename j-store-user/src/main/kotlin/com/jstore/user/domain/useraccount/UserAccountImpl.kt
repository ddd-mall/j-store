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
package com.jstore.user.domain.useraccount

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.LocalDateTime
import java.util.*

/** UserAccount 聚合根实现 */
class UserAccountImpl(
    override val id: UserId,
    override val phoneNumber: PhoneNumber,
    override var nickname: Nickname,
    override var passwordHash: Password,
    override var status: UserAccountStatus,
    override val createTime: LocalDateTime = LocalDateTime.now(),
    override var updateTime: LocalDateTime = LocalDateTime.now(),
) : UserAccount {

    override val domainEventQueue: Queue<DomainEvent> = LinkedList()

    override fun changeNickname(newNickname: Nickname): Result<Unit, BusinessError> {
        nickname = newNickname
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    override fun changePassword(newPasswordHash: Password): Result<Unit, BusinessError> {
        passwordHash = newPasswordHash
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    override fun disable(): Result<Unit, BusinessError> {
        if (status != UserAccountStatus.ACTIVE) {
            return Failure(UserAccountErrors.ILLEGAL_STATE)
        }
        status = UserAccountStatus.DISABLED
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    override fun enable(): Result<Unit, BusinessError> {
        if (status != UserAccountStatus.DISABLED) {
            return Failure(UserAccountErrors.ILLEGAL_STATE)
        }
        status = UserAccountStatus.ACTIVE
        updateTime = LocalDateTime.now()
        return Success(Unit)
    }
}
