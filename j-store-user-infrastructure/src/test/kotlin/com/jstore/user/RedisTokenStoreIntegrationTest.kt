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
package com.jstore.user

import com.jstore.user.domain.useraccount.RedisTokenStore
import com.jstore.user.domain.useraccount.RefreshTokenRotationResult
import com.jstore.user.domain.useraccount.UserId
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedisTokenStoreIntegrationTest {
    @Test
    fun `multiple sessions rotate atomically and all sessions can be revoked`() =
        withEmbeddedRedisStore { store ->
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
    fun `the same refresh token has at most one successful successor`() =
        withEmbeddedRedisStore { store ->
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

    private fun withEmbeddedRedisStore(block: (RedisTokenStore) -> Unit) =
        EmbeddedRedisTestFixture.withRedis { template ->
            block(RedisTokenStore(template))
        }
}
