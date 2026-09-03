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
package com.jstore.user.domain.useraccount

import com.jstore.authentication.principal.AccessTokenVerifier
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.authentication.principal.AuthenticatedSession
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.time.Instant
import java.util.*
import javax.crypto.SecretKey

/**
 * JWT 实现的 TokenProvider AccessToken: 15 分钟有效期，claims 包含 userId, jti, exp, iat RefreshToken: 7 天有效期
 * 使用 HS256 算法签名
 */
class JwtTokenProvider(
    accessSecret: String,
    refreshSecret: String,
    private val issuer: String,
    private val audience: String,
    private val keyId: String,
) : TokenProvider, AccessTokenVerifier {

    init {
        require(accessSecret != refreshSecret) {
            "access and refresh token secrets must be different"
        }
        require(accessSecret.toByteArray(Charsets.UTF_8).size >= 32) {
            "access token secret must be at least 32 bytes"
        }
        require(refreshSecret.toByteArray(Charsets.UTF_8).size >= 32) {
            "refresh token secret must be at least 32 bytes"
        }
        require(issuer.isNotBlank()) { "issuer must not be blank" }
        require(audience.isNotBlank()) { "audience must not be blank" }
        require(keyId.isNotBlank()) { "keyId must not be blank" }
    }

    private val accessKey: SecretKey = Keys.hmacShaKeyFor(accessSecret.toByteArray())
    private val refreshKey: SecretKey = Keys.hmacShaKeyFor(refreshSecret.toByteArray())

    companion object {
        private const val ACCESS_TOKEN_EXPIRY_SECONDS = 15L * 60 // 15 minutes
        private const val REFRESH_TOKEN_EXPIRY_SECONDS = 7L * 24 * 60 * 60 // 7 days
        private const val CLAIM_USER_ID = "userId"
        private const val CLAIM_SESSION_ID = "sid"
        private const val CLAIM_SESSION_EPOCH = "sev"
        private const val CLAIM_TOKEN_TYPE = "type"
        private const val TOKEN_TYPE_ACCESS = "access"
        private const val TOKEN_TYPE_REFRESH = "refresh"
    }

    override fun issueAccessToken(userId: UserId, sessionId: String, sessionEpoch: Long): String =
        issueToken(
            userId,
            sessionId,
            sessionEpoch,
            TOKEN_TYPE_ACCESS,
            ACCESS_TOKEN_EXPIRY_SECONDS,
            accessKey,
        )

    override fun issueRefreshToken(userId: UserId, sessionId: String, sessionEpoch: Long): String =
        issueToken(
            userId,
            sessionId,
            sessionEpoch,
            TOKEN_TYPE_REFRESH,
            REFRESH_TOKEN_EXPIRY_SECONDS,
            refreshKey,
        )

    private fun issueToken(
        userId: UserId,
        sessionId: String,
        sessionEpoch: Long,
        tokenType: String,
        expirySeconds: Long,
        signingKey: SecretKey,
    ): String {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(sessionEpoch >= 0) { "sessionEpoch must not be negative" }
        val now = Instant.now()
        return Jwts.builder()
            .header()
            .keyId(keyId)
            .and()
            .id(UUID.randomUUID().toString())
            .subject(userId.value.toString())
            .issuer(issuer)
            .audience()
            .add(audience)
            .and()
            .claim(CLAIM_USER_ID, userId.value)
            .claim(CLAIM_SESSION_ID, sessionId)
            .claim(CLAIM_SESSION_EPOCH, sessionEpoch)
            .claim(CLAIM_TOKEN_TYPE, tokenType)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expirySeconds)))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact()
    }

    override fun parseAccessToken(token: String): AuthTokenClaims? =
        parseToken(token, TOKEN_TYPE_ACCESS, accessKey)

    override fun verifyAccessToken(token: String): AuthenticatedPrincipal? =
        parseAccessToken(token)?.let { claims ->
            AuthenticatedPrincipal(
                authenticationDomain = issuer,
                accountId = claims.userId,
                session = AuthenticatedSession(claims.sessionId, claims.sessionEpoch),
            )
        }

    override fun parseRefreshToken(token: String): AuthTokenClaims? =
        parseToken(token, TOKEN_TYPE_REFRESH, refreshKey)

    private fun parseToken(
        token: String,
        expectedType: String,
        verificationKey: SecretKey,
    ): AuthTokenClaims? {
        return try {
            val signed =
                Jwts.parser()
                    .verifyWith(verificationKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token)
            if (signed.header.keyId != keyId) return null
            val claims = signed.payload
            val type = claims[CLAIM_TOKEN_TYPE] as? String
            if (type != expectedType) return null
            val userId = claims[CLAIM_USER_ID] as? Number ?: return null
            val sessionId = claims[CLAIM_SESSION_ID] as? String ?: return null
            val sessionEpoch = claims[CLAIM_SESSION_EPOCH] as? Number ?: return null
            val jti = claims.id ?: return null
            val parsedUserId = UserId(userId.toLong())
            if (claims.subject != parsedUserId.value.toString()) return null
            if (sessionId.isBlank() || sessionEpoch.toLong() < 0) return null
            AuthTokenClaims(parsedUserId, sessionId, sessionEpoch.toLong(), jti)
        } catch (_: Exception) {
            null
        }
    }
}
