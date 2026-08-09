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
package com.jstore.user.service

import com.jstore.user.api.UserProfileInfo
import com.jstore.user.api.UserProfileStatus
import com.jstore.user.domain.useraccount.UserAccountRepository
import com.jstore.user.domain.useraccount.UserAccountStatus
import com.jstore.user.domain.useraccount.UserId

class UserProfileReader(private val repository: UserAccountRepository) {
    fun findById(userId: Long): UserProfileInfo? {
        if (userId <= 0) return null
        return repository.findById(UserId(userId))?.let { account ->
            UserProfileInfo(
                userId = account.id.value,
                nickname = account.nickname.value,
                phoneNumber = account.phoneNumber.value,
                status =
                    when (account.status) {
                        UserAccountStatus.ACTIVE -> UserProfileStatus.ACTIVE
                        UserAccountStatus.DISABLED -> UserProfileStatus.DISABLED
                    },
            )
        }
    }
}
