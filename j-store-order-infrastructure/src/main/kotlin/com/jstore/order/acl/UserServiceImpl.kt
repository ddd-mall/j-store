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
package com.jstore.order.acl

import com.jstore.common.properties.PhoneNumber
import com.jstore.order.domain.order.UserInfo
import com.jstore.user.api.UserProfileQueryService
import com.jstore.user.api.UserProfileStatus

class UserServiceImpl(
    private val profiles: UserProfileQueryService,
    private val authenticationDomain: String,
) : UserService {
    override fun findUserInfo(userId: Long): UserInfo? {
        val profile = profiles.findInCurrentAuthenticationDomain(userId) ?: return null
        if (profile.status != UserProfileStatus.ACTIVE) return null
        return UserInfo(
            authenticationDomain = authenticationDomain,
            uid = profile.userId,
            phoneNumber = PhoneNumber(profile.phoneNumber),
            userName = profile.nickname,
        )
    }
}
