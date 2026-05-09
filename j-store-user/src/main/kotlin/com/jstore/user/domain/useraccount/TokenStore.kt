package com.jstore.user.domain.useraccount

/**
 * Token 存储接口（RefreshToken 存储 + AccessToken 黑名单）
 * 定义在领域层，实现在基础设施层（Redis）
 */
interface TokenStore {
    /** 存储 RefreshToken */
    fun storeRefreshToken(userId: UserId, refreshToken: String, ttlSeconds: Long)

    /** 获取存储的 RefreshToken */
    fun getRefreshToken(userId: UserId): String?

    /** 删除 RefreshToken */
    fun removeRefreshToken(userId: UserId)

    /** 将 AccessToken 加入黑名单 */
    fun blacklistAccessToken(jti: String, ttlSeconds: Long)

    /** 检查 AccessToken 是否在黑名单中 */
    fun isAccessTokenBlacklisted(jti: String): Boolean
}
