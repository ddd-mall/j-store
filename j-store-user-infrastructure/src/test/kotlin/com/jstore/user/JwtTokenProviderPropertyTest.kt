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

import com.jstore.user.domain.useraccount.JwtTokenProvider
import com.jstore.user.domain.useraccount.UserId
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.time.Instant
import java.util.Date

/**
 * Feature: user-account, Property 7: Token 签发解析 round-trip
 *
 * For any 有效的 UserId，TokenProvider 签发 AccessToken 后解析应返回相同的 UserId， 且解析结果包含 jti。签发 RefreshToken
 * 后解析应返回相同的 UserId。
 *
 * **Validates: Requirements 7.1, 9.5**
 */
class JwtTokenProviderPropertyTest :
    FunSpec({
        val accessSecret = "access-secret-key-for-jwt-must-be-at-least-32-bytes!!"
        val refreshSecret = "refresh-secret-key-for-jwt-must-be-at-least-32-bytes!"
        val tokenProvider =
            JwtTokenProvider(
                accessSecret = accessSecret,
                refreshSecret = refreshSecret,
                issuer = "j-store-test",
                audience = "j-store-api-test",
                keyId = "test-key-1",
            )

        val userIdArb: Arb<UserId> = Arb.long(1L..Long.MAX_VALUE).map { UserId(it) }

        test("access token round trip preserves user session and epoch") {
            checkAll(100, userIdArb) { userId ->
                val token = tokenProvider.issueAccessToken(userId, "session-1", 7L)
                val parsed = tokenProvider.parseAccessToken(token)
                parsed.shouldNotBeNull()
                parsed.userId shouldBe userId
                parsed.sessionId shouldBe "session-1"
                parsed.sessionEpoch shouldBe 7L
            }
        }

        test("refresh token round trip preserves user session and epoch") {
            checkAll(100, userIdArb) { userId ->
                val token = tokenProvider.issueRefreshToken(userId, "session-2", 11L)
                val parsed = tokenProvider.parseRefreshToken(token)
                parsed.shouldNotBeNull()
                parsed.userId shouldBe userId
                parsed.sessionId shouldBe "session-2"
                parsed.sessionEpoch shouldBe 11L
            }
        }

        test("access and refresh tokens cannot be verified with the other token type key") {
            val access = tokenProvider.issueAccessToken(UserId(42), "session", 1)
            val refresh = tokenProvider.issueRefreshToken(UserId(42), "session", 1)

            tokenProvider.parseRefreshToken(access) shouldBe null
            tokenProvider.parseAccessToken(refresh) shouldBe null
        }

        test("access and refresh secrets must be different") {
            shouldThrow<IllegalArgumentException> {
                JwtTokenProvider(
                    accessSecret = accessSecret,
                    refreshSecret = accessSecret,
                    issuer = "j-store-test",
                    audience = "j-store-api-test",
                    keyId = "test-key-1",
                )
            }
        }

        test("issuer audience and key id are enforced") {
            val token = tokenProvider.issueAccessToken(UserId(42), "session", 1)
            val wrongBoundary =
                JwtTokenProvider(
                    accessSecret = accessSecret,
                    refreshSecret = refreshSecret,
                    issuer = "other-issuer",
                    audience = "other-audience",
                    keyId = "other-key",
                )

            wrongBoundary.parseAccessToken(token) shouldBe null
        }

        test("subject and userId claim must identify the same account") {
            val now = Instant.now()
            val inconsistent =
                Jwts.builder()
                    .header()
                    .keyId("test-key-1")
                    .and()
                    .id("jti")
                    .subject("43")
                    .issuer("j-store-test")
                    .audience()
                    .add("j-store-api-test")
                    .and()
                    .claim("userId", 42L)
                    .claim("sid", "session")
                    .claim("sev", 1L)
                    .claim("type", "access")
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plusSeconds(60)))
                    .signWith(Keys.hmacShaKeyFor(accessSecret.toByteArray()), Jwts.SIG.HS256)
                    .compact()

            tokenProvider.parseAccessToken(inconsistent) shouldBe null
        }
    })
