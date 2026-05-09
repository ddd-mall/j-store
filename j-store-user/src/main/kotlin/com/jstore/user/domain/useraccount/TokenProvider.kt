package com.jstore.user.domain.useraccount

/**
 * 令牌提供者接口
 * 定义在领域层，实现在基础设施层（JWT）
 */
interface TokenProvider {
    /** 签发 AccessToken，返回 token 字符串 */
    fun issueAccessToken(userId: UserId): String

    /** 签发 RefreshToken，返回 token 字符串 */
    fun issueRefreshToken(userId: UserId): String

    /** 解析 AccessToken，返回 userId；无效则返回 null */
    fun parseAccessToken(token: String): UserId?

    /** 解析 RefreshToken，返回 userId；无效则返回 null */
    fun parseRefreshToken(token: String): UserId?

    /** 获取 AccessToken 的 jti */
    fun getAccessTokenJti(token: String): String?

    /** 获取 AccessToken 的剩余有效期（秒） */
    fun getAccessTokenRemainingSeconds(token: String): Long
}
