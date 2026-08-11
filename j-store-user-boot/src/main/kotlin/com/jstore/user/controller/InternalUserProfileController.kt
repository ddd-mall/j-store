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
import com.jstore.user.service.UserProfileReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/api/users")
@ConditionalOnProperty(
    prefix = "jstore.user-query.server",
    name = ["enabled"],
    havingValue = "true",
)
class InternalUserProfileController(
    private val profileReader: UserProfileReader,
    @param:Value($$"${jstore.user-query.server.token:}") private val internalToken: String,
) {
    init {
        require(internalToken.length >= 32) {
            "jstore.user-query.server.token must contain at least 32 characters"
        }
    }

    @SkipLogin
    @GetMapping("/{userId}/profile")
    fun findProfile(
        @PathVariable userId: Long,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ): ResponseEntity<UserProfileInfo> {
        if (!hasValidBearerToken(authorization)) {
            return ResponseEntity.status(401).build()
        }
        val profile = profileReader.findById(userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(profile)
    }

    private fun hasValidBearerToken(authorization: String?): Boolean {
        val presented =
            authorization?.takeIf { it.startsWith(BEARER_PREFIX) }?.substring(7) ?: return false
        return MessageDigest.isEqual(
            internalToken.toByteArray(StandardCharsets.UTF_8),
            presented.toByteArray(StandardCharsets.UTF_8),
        )
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
