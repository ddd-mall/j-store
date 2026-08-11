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
package com.jstore.shop.config

import com.jstore.user.api.UserProfileInfo
import com.jstore.user.api.UserProfileQueryService
import com.jstore.user.api.UserProfileStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MerchantUserAccountLookupConfigurationTest {
    private val profiles = mock<UserProfileQueryService>()
    private val lookup = MerchantBootConfiguration().merchantUserAccountLookup(profiles)

    @Test
    fun `merchant account lookup uses the published user profile contract`() {
        whenever(profiles.findById(42))
            .thenReturn(UserProfileInfo(42, "member", "+8613800138000", UserProfileStatus.ACTIVE))
        whenever(profiles.findById(404)).thenReturn(null)

        assertTrue(lookup.exists(42))
        assertFalse(lookup.exists(404))
    }
}
