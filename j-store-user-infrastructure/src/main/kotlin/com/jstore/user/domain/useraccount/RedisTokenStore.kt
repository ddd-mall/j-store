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

import java.util.concurrent.TimeUnit
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript

class RedisTokenStore(private val redisTemplate: StringRedisTemplate) : TokenStore {
    override fun currentSessionEpoch(userId: UserId): Long =
        redisTemplate.opsForValue().get(epochKey(userId))?.toLongOrNull() ?: 0L

    override fun storeRefreshSession(
        userId: UserId,
        sessionId: String,
        refreshTokenDigest: String,
        sessionEpoch: Long,
        ttlSeconds: Long,
    ) {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(refreshTokenDigest.isNotBlank()) { "refreshTokenDigest must not be blank" }
        require(sessionEpoch >= 0) { "sessionEpoch must not be negative" }
        require(ttlSeconds > 0) { "ttlSeconds must be positive" }
        redisTemplate
            .opsForValue()
            .set(
                sessionKey(userId, sessionId),
                sessionValue(sessionEpoch, refreshTokenDigest),
                ttlSeconds,
                TimeUnit.SECONDS,
            )
    }

    override fun rotateRefreshSession(
        userId: UserId,
        sessionId: String,
        expectedDigest: String,
        replacementDigest: String,
        sessionEpoch: Long,
        ttlSeconds: Long,
    ): RefreshTokenRotationResult {
        val result =
            redisTemplate.execute(
                ROTATE_SCRIPT,
                listOf(epochKey(userId), sessionKey(userId, sessionId)),
                sessionEpoch.toString(),
                sessionValue(sessionEpoch, expectedDigest),
                sessionValue(sessionEpoch, replacementDigest),
                ttlSeconds.toString(),
            ) ?: 0L
        return when (result) {
            1L -> RefreshTokenRotationResult.ROTATED
            -1L -> RefreshTokenRotationResult.REPLAY_DETECTED
            else -> RefreshTokenRotationResult.SESSION_NOT_FOUND
        }
    }

    override fun revokeSession(userId: UserId, sessionId: String) {
        redisTemplate.delete(sessionKey(userId, sessionId))
    }

    override fun revokeAllSessions(userId: UserId): Long =
        requireNotNull(redisTemplate.opsForValue().increment(epochKey(userId)))

    override fun isSessionActive(
        userId: UserId,
        sessionId: String,
        sessionEpoch: Long,
    ): Boolean =
        redisTemplate.execute(
            ACTIVE_SESSION_SCRIPT,
            listOf(epochKey(userId), sessionKey(userId, sessionId)),
            sessionEpoch.toString(),
            "$sessionEpoch:",
        ) == 1L

    private fun epochKey(userId: UserId) = "$SESSION_EPOCH_KEY_PREFIX${userId.value}"

    private fun sessionKey(userId: UserId, sessionId: String) =
        "$SESSION_KEY_PREFIX${userId.value}:$sessionId"

    private fun sessionValue(sessionEpoch: Long, digest: String) = "$sessionEpoch:$digest"

    private companion object {
        const val SESSION_KEY_PREFIX = "auth_session:"
        const val SESSION_EPOCH_KEY_PREFIX = "auth_session_epoch:"

        val ROTATE_SCRIPT =
            DefaultRedisScript(
                """
                local epoch = redis.call('GET', KEYS[1]) or '0'
                if epoch ~= ARGV[1] then return 0 end
                local current = redis.call('GET', KEYS[2])
                if not current then return 0 end
                if current ~= ARGV[2] then
                    redis.call('DEL', KEYS[2])
                    return -1
                end
                redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[4])
                return 1
                """
                    .trimIndent(),
                Long::class.java,
            )

        val ACTIVE_SESSION_SCRIPT =
            DefaultRedisScript(
                """
                local epoch = redis.call('GET', KEYS[1]) or '0'
                if epoch ~= ARGV[1] then return 0 end
                local session = redis.call('GET', KEYS[2])
                if not session then return 0 end
                if string.sub(session, 1, string.len(ARGV[2])) ~= ARGV[2] then return 0 end
                return 1
                """
                    .trimIndent(),
                Long::class.java,
            )
    }
}
