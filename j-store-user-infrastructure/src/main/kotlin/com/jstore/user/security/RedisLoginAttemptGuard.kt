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
package com.jstore.user.security

import com.jstore.common.properties.PhoneNumber
import com.jstore.user.domain.useraccount.LoginAttemptGuard
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript

class RedisLoginAttemptGuard(private val redisTemplate: StringRedisTemplate) : LoginAttemptGuard {
    override fun isAllowed(phoneNumber: PhoneNumber): Boolean {
        val stored = redisTemplate.opsForValue().get(key(phoneNumber)) ?: return true
        val failures = stored.toIntOrNull() ?: return false
        return failures < MAX_FAILURES
    }

    override fun recordFailure(phoneNumber: PhoneNumber) {
        redisTemplate.execute(
            RECORD_FAILURE_SCRIPT,
            listOf(key(phoneNumber)),
            FAILURE_WINDOW_SECONDS.toString(),
        )
    }

    override fun reset(phoneNumber: PhoneNumber) {
        redisTemplate.delete(key(phoneNumber))
    }

    private fun key(phoneNumber: PhoneNumber) = "$KEY_PREFIX${phoneNumber.value}"

    private companion object {
        const val KEY_PREFIX = "login_failures:"
        const val MAX_FAILURES = 5
        const val FAILURE_WINDOW_SECONDS = 900L

        val RECORD_FAILURE_SCRIPT =
            DefaultRedisScript(
                """
                local count = redis.call('INCR', KEYS[1])
                if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
                return count
                """
                    .trimIndent(),
                Long::class.java,
            )
    }
}
