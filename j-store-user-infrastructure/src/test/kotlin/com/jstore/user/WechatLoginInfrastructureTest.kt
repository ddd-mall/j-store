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

import com.jstore.common.properties.PhoneNumber
import com.jstore.user.domain.useraccount.PhoneVerificationProof
import com.jstore.user.oidc.RedisLoginIntentStore
import com.jstore.user.security.RedisPhoneVerificationGateway
import kotlin.test.*
import org.junit.jupiter.api.Test

class WechatLoginInfrastructureTest {
    @Test
    fun `wechat challenge cannot be consumed by registration or another intent`() =
        EmbeddedRedisTestFixture.withRedis { redis ->
            val gateway =
                RedisPhoneVerificationGateway(redis, "test-verification-key-for-integration-only")
            val phone = PhoneNumber("+8613800138000")
            val issued = gateway.createChallenge(phone, "wechat-binding", "intent-a")!!
            val proof = PhoneVerificationProof(issued.challenge.challengeId, issued.code)
            assertFalse(gateway.consumeChallenge(phone, proof, "registration", ""))
        }

    @Test
    fun `login state is browser bound and single use`() =
        EmbeddedRedisTestFixture.withRedis { redis ->
            val store = RedisLoginIntentStore(redis)
            val state = store.create("browser-a", "payload", 300)
            assertNull(store.consume(state, "browser-b"))
            assertEquals("payload", store.consume(state, "browser-a"))
            assertNull(store.consume(state, "browser-a"))
        }
}
