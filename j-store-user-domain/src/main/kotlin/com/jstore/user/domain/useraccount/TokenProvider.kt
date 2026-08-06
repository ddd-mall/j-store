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

data class AuthTokenClaims(
    val userId: UserId,
    val sessionId: String,
    val sessionEpoch: Long,
    val jti: String,
)

/** 令牌提供者接口，定义在领域层，实现在基础设施层。 */
interface TokenProvider {
    fun issueAccessToken(userId: UserId, sessionId: String, sessionEpoch: Long): String

    fun issueRefreshToken(userId: UserId, sessionId: String, sessionEpoch: Long): String

    fun parseAccessToken(token: String): AuthTokenClaims?

    fun parseRefreshToken(token: String): AuthTokenClaims?
}
