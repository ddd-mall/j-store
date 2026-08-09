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
package com.jstore.user.controller

import com.jstore.authentication.annotation.SkipLogin
import com.jstore.user.api.UserProfileInfo
import com.jstore.user.api.UserProfileStatus
import com.jstore.user.service.UserProfileReader
import kotlin.test.Test
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class InternalUserProfileControllerTest {
    private val profileReader = mock<UserProfileReader>()
    private val token = "a".repeat(32)
    private val mvc =
        MockMvcBuilders.standaloneSetup(InternalUserProfileController(profileReader, token)).build()

    @Test
    fun `correct internal bearer token returns the complete user profile`() {
        whenever(profileReader.findById(42))
            .thenReturn(UserProfileInfo(42, "buyer", "+8613800138000", UserProfileStatus.ACTIVE))

        mvc.perform(get("/internal/api/users/42/profile").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(42))
            .andExpect(jsonPath("$.nickname").value("buyer"))
            .andExpect(jsonPath("$.phoneNumber").value("+8613800138000"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `missing or incorrect internal bearer token is rejected without profile disclosure`() {
        mvc.perform(get("/internal/api/users/42/profile")).andExpect(status().isUnauthorized)
        mvc.perform(
                get("/internal/api/users/42/profile")
                    .header("Authorization", "Bearer ${"b".repeat(32)}")
            )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `missing user returns not found`() {
        whenever(profileReader.findById(404)).thenReturn(null)

        mvc.perform(get("/internal/api/users/404/profile").header("Authorization", "Bearer $token"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `internal endpoint bypasses end user login and performs its own service authentication`() {
        val method =
            InternalUserProfileController::class.java.methods.single { it.name == "findProfile" }

        assertTrue(method.isAnnotationPresent(SkipLogin::class.java))
    }
}
