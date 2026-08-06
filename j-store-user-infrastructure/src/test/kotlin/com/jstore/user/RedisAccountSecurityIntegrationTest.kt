package com.jstore.user

import com.jstore.common.properties.PhoneNumber
import com.jstore.user.domain.useraccount.PhoneVerificationProof
import com.jstore.user.security.RedisLoginAttemptGuard
import com.jstore.user.security.RedisPhoneVerificationGateway
import java.net.ServerSocket
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

class RedisAccountSecurityIntegrationTest {
    private val phone = PhoneNumber("+8613800138000")
    private val otherPhone = PhoneNumber("+8613900139000")

    @Test
    fun `challenge is rate limited phone bound one time and stored as HMAC only`() =
        withRedis { template ->
            val gateway =
                RedisPhoneVerificationGateway(template, "hmac-secret-at-least-thirty-two-bytes")
            val issued = requireNotNull(gateway.createChallenge(phone))

            assertNull(gateway.createChallenge(phone))
            val stored =
                template.opsForValue().get("phone_verification:${issued.challenge.challengeId}")
            assertEquals(64, stored?.length)
            assertNotEquals(issued.code, stored)
            assertFalse(stored.orEmpty().contains(issued.code))

            assertFalse(
                gateway.consumeChallenge(
                    otherPhone,
                    PhoneVerificationProof(issued.challenge.challengeId, issued.code),
                )
            )
            assertFalse(
                gateway.consumeChallenge(
                    phone,
                    PhoneVerificationProof(issued.challenge.challengeId, issued.code),
                )
            )

            template.delete("phone_verification_rate:${phone.value}")
            val second = requireNotNull(gateway.createChallenge(phone))
            val proof = PhoneVerificationProof(second.challenge.challengeId, second.code)
            assertTrue(gateway.consumeChallenge(phone, proof))
            assertFalse(gateway.consumeChallenge(phone, proof))
        }

    @Test
    fun `expired challenge cannot be consumed`() = withRedis { template ->
        val gateway =
            RedisPhoneVerificationGateway(
                template,
                "hmac-secret-at-least-thirty-two-bytes",
                challengeTtlSeconds = 1,
                sendIntervalSeconds = 1,
            )
        val issued = requireNotNull(gateway.createChallenge(phone))
        Thread.sleep(1100)

        assertFalse(
            gateway.consumeChallenge(
                phone,
                PhoneVerificationProof(issued.challenge.challengeId, issued.code),
            )
        )
    }

    @Test
    fun `login failures are shared and success reset removes the block`() = withRedis { template ->
        val first = RedisLoginAttemptGuard(template)
        val second = RedisLoginAttemptGuard(template)

        repeat(5) { first.recordFailure(phone) }
        assertFalse(second.isAllowed(phone))

        second.reset(phone)
        assertTrue(first.isAllowed(phone))

        template.opsForValue().set("login_failures:${phone.value}", "corrupt")
        assertFalse(first.isAllowed(phone))
    }

    private fun withRedis(block: (StringRedisTemplate) -> Unit) {
        val port = ServerSocket(0).use { it.localPort }
        val process =
            ProcessBuilder(
                    "redis-server",
                    "--bind",
                    "127.0.0.1",
                    "--port",
                    port.toString(),
                    "--save",
                    "",
                    "--appendonly",
                    "no",
                )
                .redirectErrorStream(true)
                .start()
        val connectionFactory = LettuceConnectionFactory("127.0.0.1", port)
        try {
            connectionFactory.afterPropertiesSet()
            connectionFactory.start()
            val template = StringRedisTemplate(connectionFactory)
            template.afterPropertiesSet()
            waitUntilReady(template)
            block(template)
        } finally {
            connectionFactory.destroy()
            process.destroy()
            process.waitFor(Duration.ofSeconds(5))
        }
    }

    private fun waitUntilReady(template: StringRedisTemplate) {
        repeat(50) {
            try {
                if (template.connectionFactory?.connection?.ping() == "PONG") return
            } catch (_: Exception) {
                Thread.sleep(20)
            }
        }
        error("Redis did not become ready")
    }
}
