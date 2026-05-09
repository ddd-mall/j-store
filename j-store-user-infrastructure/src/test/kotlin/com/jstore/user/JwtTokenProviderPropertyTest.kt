package com.jstore.user

import com.jstore.user.domain.useraccount.JwtTokenProvider
import com.jstore.user.domain.useraccount.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * Feature: user-account, Property 7: Token 签发解析 round-trip
 *
 * For any 有效的 UserId，TokenProvider 签发 AccessToken 后解析应返回相同的 UserId，
 * 且解析结果包含 jti。签发 RefreshToken 后解析应返回相同的 UserId。
 *
 * **Validates: Requirements 7.1, 9.5**
 */
class JwtTokenProviderPropertyTest : FunSpec({

    // 256-bit secret key for HS256 (at least 32 bytes)
    val testSecret = "test-secret-key-for-jwt-must-be-at-least-32-bytes-long!!"
    val tokenProvider = JwtTokenProvider(testSecret)

    val userIdArb: Arb<UserId> = Arb.long(1L..Long.MAX_VALUE).map { UserId(it) }

    test("issueAccessToken then parseAccessToken should return same UserId") {
        checkAll(100, userIdArb) { userId ->
            val token = tokenProvider.issueAccessToken(userId)
            val parsed = tokenProvider.parseAccessToken(token)
            parsed shouldBe userId
        }
    }

    test("issueAccessToken then getAccessTokenJti should return non-null") {
        checkAll(100, userIdArb) { userId ->
            val token = tokenProvider.issueAccessToken(userId)
            val jti = tokenProvider.getAccessTokenJti(token)
            jti.shouldNotBeNull()
        }
    }

    test("issueRefreshToken then parseRefreshToken should return same UserId") {
        checkAll(100, userIdArb) { userId ->
            val token = tokenProvider.issueRefreshToken(userId)
            val parsed = tokenProvider.parseRefreshToken(token)
            parsed shouldBe userId
        }
    }
})
