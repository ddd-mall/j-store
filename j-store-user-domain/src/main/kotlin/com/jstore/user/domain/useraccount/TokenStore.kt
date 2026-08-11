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

enum class RefreshTokenRotationResult {
    ROTATED,
    REPLAY_DETECTED,
    SESSION_NOT_FOUND,
}

/** 服务端认证会话存储端口。Refresh Token 参数必须是不可逆摘要。 */
interface TokenStore {
    fun currentSessionEpoch(userId: UserId): Long

    fun storeRefreshSession(
        userId: UserId,
        sessionId: String,
        refreshTokenDigest: String,
        sessionEpoch: Long,
        ttlSeconds: Long,
    )

    fun rotateRefreshSession(
        userId: UserId,
        sessionId: String,
        expectedDigest: String,
        replacementDigest: String,
        sessionEpoch: Long,
        ttlSeconds: Long,
    ): RefreshTokenRotationResult

    fun revokeSession(userId: UserId, sessionId: String)

    /** 递增用户会话代次，使该用户所有既有 Access/Refresh Token 立即失效。 */
    fun revokeAllSessions(userId: UserId): Long

    fun isSessionActive(userId: UserId, sessionId: String, sessionEpoch: Long): Boolean
}
