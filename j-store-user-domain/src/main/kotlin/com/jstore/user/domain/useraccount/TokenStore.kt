package com.jstore.user.domain.useraccount

enum class RefreshTokenRotationResult {
    ROTATED,
    REPLAY_DETECTED,
    SESSION_NOT_FOUND,
}

/** 服务端认证会话存储端口。Refresh Token 参数必须是不可逆摘要。 */
interface TokenStore {
    fun currentSessionEpoch(userId: UserId): Long

    fun storeRefreshSession(
        userId: UserId,
        sessionId: String,
        refreshTokenDigest: String,
        sessionEpoch: Long,
        ttlSeconds: Long,
    )

    fun rotateRefreshSession(
        userId: UserId,
        sessionId: String,
        expectedDigest: String,
        replacementDigest: String,
        sessionEpoch: Long,
        ttlSeconds: Long,
    ): RefreshTokenRotationResult

    fun revokeSession(userId: UserId, sessionId: String)

    /** 递增用户会话代次，使该用户所有既有 Access/Refresh Token 立即失效。 */
    fun revokeAllSessions(userId: UserId): Long

    fun isSessionActive(userId: UserId, sessionId: String, sessionEpoch: Long): Boolean
}
