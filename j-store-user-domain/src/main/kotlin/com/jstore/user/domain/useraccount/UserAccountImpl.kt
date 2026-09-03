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
import com.jstore.common.framework.EventRecordingAggregateRoot
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.event.UserAccountRegisteredEvent
import java.time.LocalDateTime

/** UserAccount 聚合根实现 */
class UserAccountImpl(
    override val id: UserId,
    override val phoneNumber: PhoneNumber,
    nickname: Nickname,
    passwordHash: Password,
    status: UserAccountStatus,
    override val createTime: LocalDateTime = LocalDateTime.now(),
    updateTime: LocalDateTime = LocalDateTime.now(),
) : EventRecordingAggregateRoot<UserId>(), UserAccount {
    private var _nickname = nickname
    private var _passwordHash = passwordHash
    private var _status = status
    private var _updateTime = updateTime

    override val nickname: Nickname
        get() = _nickname

    override val passwordHash: Password
        get() = _passwordHash

    override val status: UserAccountStatus
        get() = _status

    override val updateTime: LocalDateTime
        get() = _updateTime

    internal fun recordRegistered() {
        raise(UserAccountRegisteredEvent(userId = id, phoneNumber = phoneNumber))
    }

    override fun changeNickname(newNickname: Nickname): Result<Unit, BusinessError> {
        _nickname = newNickname
        _updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    override fun changePassword(newPasswordHash: Password): Result<Unit, BusinessError> {
        _passwordHash = newPasswordHash
        _updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    override fun disable(): Result<Unit, BusinessError> {
        if (_status != UserAccountStatus.ACTIVE) {
            return Failure(UserAccountErrors.ILLEGAL_STATE)
        }
        _status = UserAccountStatus.DISABLED
        _updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    override fun enable(): Result<Unit, BusinessError> {
        if (_status != UserAccountStatus.DISABLED) {
            return Failure(UserAccountErrors.ILLEGAL_STATE)
        }
        _status = UserAccountStatus.ACTIVE
        _updateTime = LocalDateTime.now()
        return Success(Unit)
    }
}
