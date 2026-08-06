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
