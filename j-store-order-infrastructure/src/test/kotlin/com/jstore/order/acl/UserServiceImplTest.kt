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

import com.jstore.user.api.UserProfileInfo
import com.jstore.user.api.UserProfileQueryService
import com.jstore.user.api.UserProfileStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UserServiceImplTest {
    private val profiles = mock<UserProfileQueryService>()
    private val service = UserServiceImpl(profiles, "issuer-a")

    @Test
    fun `active profile is translated into order local user info`() {
        whenever(profiles.findInCurrentAuthenticationDomain(42))
            .thenReturn(UserProfileInfo(42, "buyer", "+8613800138000", UserProfileStatus.ACTIVE))

        val info = service.findUserInfo(42)!!

        assertEquals(42, info.uid)
        assertEquals("buyer", info.userName)
        assertEquals("+8613800138000", info.phoneNumber?.value)
    }

    @Test
    fun `disabled profile is not an eligible order buyer`() {
        whenever(profiles.findInCurrentAuthenticationDomain(42))
            .thenReturn(UserProfileInfo(42, "buyer", "+8613800138000", UserProfileStatus.DISABLED))

        assertNull(service.findUserInfo(42))
    }
}
