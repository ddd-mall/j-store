package com.jstore.user

import com.jstore.user.domain.useraccount.RedisTokenStore
import com.jstore.user.domain.useraccount.RefreshTokenRotationResult
import com.jstore.user.domain.useraccount.UserId
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

class RedisTokenStoreIntegrationTest {
    @Test
    fun `multiple sessions rotate atomically and all sessions can be revoked`() =
        withRedis { store ->
            val userId = UserId(42)
            val epoch = store.currentSessionEpoch(userId)
            store.storeRefreshSession(userId, "phone", "digest-phone", epoch, 600)
            store.storeRefreshSession(userId, "web", "digest-web", epoch, 600)

            assertTrue(store.isSessionActive(userId, "phone", epoch))
            assertTrue(store.isSessionActive(userId, "web", epoch))
            assertEquals(
                RefreshTokenRotationResult.ROTATED,
                store.rotateRefreshSession(
                    userId,
                    "phone",
                    "digest-phone",
                    "digest-phone-next",
                    epoch,
                    600,
                ),
            )
            assertEquals(
                RefreshTokenRotationResult.REPLAY_DETECTED,
                store.rotateRefreshSession(
                    userId,
                    "phone",
                    "digest-phone",
                    "attacker-next",
                    epoch,
                    600,
                ),
            )
            assertFalse(store.isSessionActive(userId, "phone", epoch))
            assertTrue(store.isSessionActive(userId, "web", epoch))

            val newEpoch = store.revokeAllSessions(userId)
            assertEquals(epoch + 1, newEpoch)
            assertFalse(store.isSessionActive(userId, "web", epoch))
        }

    @Test
    fun `the same refresh token has at most one successful successor`() = withRedis { store ->
        val userId = UserId(84)
        val epoch = store.currentSessionEpoch(userId)
        store.storeRefreshSession(userId, "session", "old-digest", epoch, 600)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val calls =
                (1..2).map { index ->
                    pool.submit(
                        Callable {
                            start.await()
                            store.rotateRefreshSession(
                                userId,
                                "session",
                                "old-digest",
                                "next-$index",
                                epoch,
                                600,
                            )
                        }
                    )
                }
            start.countDown()
            val results = calls.map { it.get() }
            assertEquals(1, results.count { it == RefreshTokenRotationResult.ROTATED })
            assertEquals(1, results.count { it == RefreshTokenRotationResult.REPLAY_DETECTED })
            assertFalse(store.isSessionActive(userId, "session", epoch))
        } finally {
            pool.shutdownNow()
        }
    }

    private fun withRedis(block: (RedisTokenStore) -> Unit) {
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
            block(RedisTokenStore(template))
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
