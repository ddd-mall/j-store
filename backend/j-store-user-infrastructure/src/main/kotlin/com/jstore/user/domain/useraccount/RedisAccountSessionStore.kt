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

import com.jstore.authentication.principal.AuthenticatedAccountId
import com.jstore.authentication.principal.AuthenticatedSessionStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript

class RedisAccountSessionStore(
    private val redis: StringRedisTemplate,
    private val namespace: String,
) : AccountSessionStore, AuthenticatedSessionStore {
  init {
    require(namespace.isNotBlank())
  }

  override fun currentSessionEpoch(userId: UserId): Long =
      requireNotNull(redis.execute(EPOCH, listOf(epochKey(userId)), seed(), "0")).toLong()

  override fun createSession(
      userId: UserId,
      sessionId: String,
      sessionEpoch: Long,
      ttlSeconds: Long,
  ): Boolean {
    require(sessionId.isNotBlank() && sessionEpoch >= 0 && ttlSeconds > 0)
    return redis.execute(
        CREATE,
        listOf(epochKey(userId), sessionKey(userId, sessionId)),
        sessionEpoch.toString(),
        ttlSeconds.toString(),
    ) == 1L
  }

  override fun revokeSession(userId: UserId, sessionId: String) {
    redis.delete(sessionKey(userId, sessionId))
  }

  override fun revokeAllSessions(userId: UserId): Long =
      requireNotNull(redis.execute(EPOCH, listOf(epochKey(userId)), seed(), "1")).toLong()

  override fun isSessionActive(userId: UserId, sessionId: String, sessionEpoch: Long): Boolean =
      redis.execute(
          ACTIVE,
          listOf(epochKey(userId), sessionKey(userId, sessionId)),
          sessionEpoch.toString(),
      ) == 1L

  override fun isSessionActive(
      accountId: AuthenticatedAccountId,
      sessionId: String,
      sessionEpoch: Long,
  ) = isSessionActive(UserId(accountId.value), sessionId, sessionEpoch)

  private fun epochKey(id: UserId) = "$namespace:epoch:${id.value}"

  private fun sessionKey(id: UserId, sid: String) = "$namespace:session:${id.value}:$sid"

  private fun seed() = java.security.SecureRandom().nextLong(1, 1L shl 52).toString()

  companion object {
    private val EPOCH =
        DefaultRedisScript(
            """
            if not redis.call('GET', KEYS[1]) then redis.call('SET', KEYS[1], ARGV[1]) end
            if ARGV[2] == '1' then redis.call('INCR', KEYS[1]) end
            return redis.call('GET', KEYS[1])
            """
                .trimIndent(),
            String::class.java,
        )
    private val CREATE =
        DefaultRedisScript(
            """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])
            return 1
            """
                .trimIndent(),
            Long::class.java,
        )
    private val ACTIVE =
        DefaultRedisScript(
            """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
            return 1
            """
                .trimIndent(),
            Long::class.java,
        )
  }
}
