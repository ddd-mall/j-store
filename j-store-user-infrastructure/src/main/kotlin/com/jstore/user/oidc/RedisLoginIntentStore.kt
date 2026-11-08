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
package com.jstore.user.oidc

import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript

/** Shared browser-bound single-use state; payloads never contain upstream credentials. */
class RedisLoginIntentStore(private val redis: StringRedisTemplate) {
  fun create(browserId: String, payload: String, ttlSeconds: Long): String {
    require(browserId.isNotBlank() && ttlSeconds in 1..600)
    val id = randomId()
    check(
        redis
            .opsForValue()
            .setIfAbsent(
                "oidc:intent:$id",
                "$browserId\n$payload",
                Duration.ofSeconds(ttlSeconds),
            ) == true
    )
    return id
  }

  fun consume(id: String, browserId: String): String? {
    if (!id.matches(Regex("[A-Za-z0-9_-]{43}")) || browserId.isBlank()) return null
    return redis.execute(CONSUME, listOf("oidc:intent:$id"), "$browserId\n", "1")
  }

  fun read(id: String, browserId: String): String? =
      redis.execute(CONSUME, listOf("oidc:intent:$id"), "$browserId\n", "0")

  companion object {
    fun randomId(): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))

    private val CONSUME =
        DefaultRedisScript(
            """
            local value = redis.call('GET', KEYS[1])
            if not value then return nil end
            if string.sub(value, 1, string.len(ARGV[1])) ~= ARGV[1] then return nil end
            if ARGV[2] == '1' then redis.call('DEL', KEYS[1]) end
            return string.sub(value, string.len(ARGV[1]) + 1)
            """
                .trimIndent(),
            String::class.java,
        )
  }
}
