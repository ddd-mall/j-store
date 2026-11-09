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

import com.jstore.user.domain.useraccount.RedisAccountSessionStore
import com.jstore.user.domain.useraccount.UserId
import kotlin.test.*

class RedisAccountSessionStoreIntegrationTest {
  @Test
  fun `independent sessions and issuer namespaces cannot revoke one another`() =
      EmbeddedRedisTestFixture.withRedis { redis ->
        val first = RedisAccountSessionStore(redis, "first-issuer")
        val other = RedisAccountSessionStore(redis, "other-issuer")
        val id = UserId(42)
        val epoch = first.currentSessionEpoch(id)
        val otherEpoch = other.currentSessionEpoch(id)
        assertTrue(first.createSession(id, "phone", epoch, 600))
        assertTrue(first.createSession(id, "web", epoch, 600))
        assertTrue(other.createSession(id, "web", otherEpoch, 600))
        first.revokeSession(id, "phone")
        assertFalse(first.isSessionActive(id, "phone", epoch))
        assertTrue(first.isSessionActive(id, "web", epoch))
        assertEquals(epoch + 1, first.revokeAllSessions(id))
        assertFalse(first.isSessionActive(id, "web", epoch))
        assertTrue(other.isSessionActive(id, "web", otherEpoch))
      }

  @Test
  fun `a stale login cannot recreate a session after global revocation`() =
      EmbeddedRedisTestFixture.withRedis { redis ->
        val store = RedisAccountSessionStore(redis, "issuer")
        val id = UserId(84)
        val oldEpoch = store.currentSessionEpoch(id)
        val epoch = store.revokeAllSessions(id)
        assertFalse(store.createSession(id, "stale-login", oldEpoch, 600))
        assertFalse(store.isSessionActive(id, "stale-login", oldEpoch))
        assertTrue(store.createSession(id, "fresh-login", epoch, 600))
        assertTrue(store.isSessionActive(id, "fresh-login", epoch))
      }

  @Test
  fun `lost epoch state cannot reactivate old credentials after reinitialization`() =
      EmbeddedRedisTestFixture.withRedis { redis ->
        val store = RedisAccountSessionStore(redis, "issuer")
        val id = UserId(99)
        val old = store.currentSessionEpoch(id)
        assertTrue(store.createSession(id, "old", old, 600))
        store.revokeAllSessions(id)
        redis.delete("issuer:epoch:99")
        assertFalse(store.isSessionActive(id, "old", old))
        assertNotEquals(old, store.currentSessionEpoch(id))
        assertFalse(store.isSessionActive(id, "old", old))
      }
}
