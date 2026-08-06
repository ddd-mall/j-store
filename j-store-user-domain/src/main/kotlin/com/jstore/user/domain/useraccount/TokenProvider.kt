package com.jstore.user.domain.useraccount

data class AuthTokenClaims(
    val userId: UserId,
    val sessionId: String,
    val sessionEpoch: Long,
    val jti: String,
)

/** 令牌提供者接口，定义在领域层，实现在基础设施层。 */
interface TokenProvider {
    fun issueAccessToken(userId: UserId, sessionId: String, sessionEpoch: Long): String

    fun issueRefreshToken(userId: UserId, sessionId: String, sessionEpoch: Long): String

    fun parseAccessToken(token: String): AuthTokenClaims?

    fun parseRefreshToken(token: String): AuthTokenClaims?
}
