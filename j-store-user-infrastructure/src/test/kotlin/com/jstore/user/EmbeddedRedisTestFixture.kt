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

import java.net.ServerSocket
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import redis.embedded.RedisServer

internal object EmbeddedRedisTestFixture {
    fun <T> withRedis(block: (StringRedisTemplate) -> T): T {
        val port = ServerSocket(0).use { it.localPort }
        val server =
            RedisServer.newRedisServer()
                .port(port)
                .setting("bind 127.0.0.1")
                .setting("save \"\"")
                .setting("appendonly no")
                .build()
        server.start()
        val connectionFactory = LettuceConnectionFactory("127.0.0.1", port)
        try {
            connectionFactory.afterPropertiesSet()
            connectionFactory.start()
            val template = StringRedisTemplate(connectionFactory)
            template.afterPropertiesSet()
            check(template.connectionFactory?.connection?.ping() == "PONG") {
                "Embedded Redis did not become ready"
            }
            return block(template)
        } finally {
            connectionFactory.destroy()
            server.stop()
        }
    }
}
