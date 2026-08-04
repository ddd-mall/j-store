package com.jstore.user.domain.useraccount

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.time.Instant
import java.util.*
import javax.crypto.SecretKey

/**
 * JWT 实现的 TokenProvider AccessToken: 15 分钟有效期，claims 包含 userId, jti, exp, iat RefreshToken: 7 天有效期
 * 使用 HS256 算法签名
 */
class JwtTokenProvider(secretKeyString: String) : TokenProvider {

    private val secretKey: SecretKey = Keys.hmacShaKeyFor(secretKeyString.toByteArray())

    companion object {
        private const val ACCESS_TOKEN_EXPIRY_SECONDS = 15L * 60 // 15 minutes
        private const val REFRESH_TOKEN_EXPIRY_SECONDS = 7L * 24 * 60 * 60 // 7 days
        private const val CLAIM_USER_ID = "userId"
        private const val CLAIM_TOKEN_TYPE = "type"
        private const val TOKEN_TYPE_ACCESS = "access"
        private const val TOKEN_TYPE_REFRESH = "refresh"
    }

    override fun issueAccessToken(userId: UserId): String {
        val now = Instant.now()
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .claim(CLAIM_USER_ID, userId.value)
            .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ACCESS_TOKEN_EXPIRY_SECONDS)))
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()
    }

    override fun issueRefreshToken(userId: UserId): String {
        val now = Instant.now()
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .claim(CLAIM_USER_ID, userId.value)
            .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(REFRESH_TOKEN_EXPIRY_SECONDS)))
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()
    }

    override fun parseAccessToken(token: String): UserId? {
        return parseToken(token, TOKEN_TYPE_ACCESS)
    }

    override fun parseRefreshToken(token: String): UserId? {
        return parseToken(token, TOKEN_TYPE_REFRESH)
    }

    override fun getAccessTokenJti(token: String): String? {
        return try {
            val claims =
                Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload
            claims.id
        } catch (_: Exception) {
            null
        }
    }

    override fun getAccessTokenRemainingSeconds(token: String): Long {
        return try {
            val claims =
                Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload
            val expiration = claims.expiration.toInstant()
            val remaining = expiration.epochSecond - Instant.now().epochSecond
            if (remaining > 0) remaining else 0
        } catch (_: Exception) {
            0
        }
    }

    private fun parseToken(token: String, expectedType: String): UserId? {
        return try {
            val claims =
                Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload
            val type = claims[CLAIM_TOKEN_TYPE] as? String
            if (type != expectedType) return null
            val userId = claims[CLAIM_USER_ID] as? Number ?: return null
            UserId(userId.toLong())
        } catch (_: Exception) {
            null
        }
    }
}
