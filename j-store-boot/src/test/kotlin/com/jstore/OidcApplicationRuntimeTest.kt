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
package com.jstore

import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.net.*
import java.net.http.*
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.time.Duration
import java.util.Base64
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import redis.embedded.RedisServer

class OidcApplicationRuntimeTest {
    @Test
    fun `real application serves OIDC and persists browser sessions in Redis`() {
        val port = ServerSocket(0).use { it.localPort }
        val redis =
            RedisServer.newRedisServer()
                .port(port)
                .setting("bind 127.0.0.1")
                .setting("save \"\"")
                .setting("appendonly no")
                .build()
        val keyFile = Files.createTempFile("jstore-runtime-test-key-", ".json")
        val key =
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
                as RSAPrivateCrtKey
        fun encoded(number: java.math.BigInteger): String {
            val bytes =
                number.toByteArray().let {
                    if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
                }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
        Files.writeString(
            keyFile,
            ObjectMapper()
                .writeValueAsString(
                    mapOf(
                        "keys" to
                            listOf(
                                mapOf(
                                    "kty" to "RSA",
                                    "kid" to "test",
                                    "n" to encoded(key.modulus),
                                    "e" to encoded(key.publicExponent),
                                    "d" to encoded(key.privateExponent),
                                )
                            )
                    )
                ),
        )
        redis.start()
        try {
            EmbeddedPostgres.builder().start().use { postgres ->
                SpringApplicationBuilder(JStoreOrderBootApplication::class.java)
                    .web(WebApplicationType.SERVLET)
                    .profiles("local")
                    .run(
                        "--server.address=127.0.0.1",
                        "--server.port=0",
                        "--spring.datasource.url=${postgres.getJdbcUrl("postgres", "postgres")}&currentSchema=develop",
                        "--spring.flyway.default-schema=develop",
                        "--spring.flyway.schemas=develop",
                        "--spring.flyway.create-schemas=true",
                        "--spring.datasource.username=postgres",
                        "--spring.datasource.password=",
                        "--spring.data.redis.host=127.0.0.1",
                        "--spring.data.redis.port=$port",
                        "--jstore.oidc.issuer=https://identity.example",
                        "--jstore.oidc.jwk-set-location=${keyFile.toUri()}",
                        "--jstore.oidc.active-key-id=test",
                        "--jstore.oidc.clients[0].id=web",
                        "--jstore.oidc.clients[0].redirects=https://client.example/callback",
                        "--account.phone-verification.hmac-secret=runtime-test-verification-hmac-secret",
                    )
                    .use { context ->
                        val actualPort =
                            (context as ServletWebServerApplicationContext).webServer.port
                        val client =
                            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
                        fun get(path: String) =
                            client.send(
                                HttpRequest.newBuilder(URI("http://127.0.0.1:$actualPort$path"))
                                    .timeout(Duration.ofSeconds(5))
                                    .GET()
                                    .build(),
                                HttpResponse.BodyHandlers.ofString(),
                            )
                        val discovery = get("/.well-known/openid-configuration")
                        assertEquals(200, discovery.statusCode(), discovery.body())
                        assertEquals(
                            "https://identity.example",
                            ObjectMapper().readTree(discovery.body()).path("issuer").asText(),
                        )
                        val page = get("/login")
                        assertEquals(200, page.statusCode(), page.body())
                        assertTrue(
                            page.headers().allValues("Set-Cookie").any {
                                it.startsWith("SESSION=") && it.contains("HttpOnly")
                            }
                        )
                        assertTrue(
                            context
                                .getBeansOfType(
                                    Class.forName("org.springframework.session.SessionRepository")
                                )
                                .isNotEmpty()
                        )
                        assertTrue(
                            context
                                .getBeansOfType(
                                    Class.forName(
                                        "com.jstore.authentication.principal.AuthenticatedSessionStore"
                                    )
                                )
                                .isNotEmpty()
                        )
                        val protected = get("/api/users/me")
                        assertEquals(401, protected.statusCode(), protected.body())
                        assertTrue(protected.body().contains("errorCode"))
                    }
            }
        } finally {
            redis.stop()
            Files.deleteIfExists(keyFile)
        }
    }
}
