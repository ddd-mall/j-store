package com.jstore.user.domain.useraccount

import java.util.concurrent.TimeUnit
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Redis 实现的 TokenStore
 * - RefreshToken: key = "refresh_token:{userId.value}", value = refreshToken, TTL in seconds
 * - AccessToken 黑名单: key = "token_blacklist:{jti}", value = "1", TTL in seconds todo:
 *   写redis的逻辑之后需要迁移到sdk中,在业务应用中实现
 */
class RedisTokenStore(private val redisTemplate: StringRedisTemplate) : TokenStore {

    companion object {
        private const val REFRESH_TOKEN_KEY_PREFIX = "refresh_token:"
        private const val TOKEN_BLACKLIST_KEY_PREFIX = "token_blacklist:"
    }

    override fun storeRefreshToken(userId: UserId, refreshToken: String, ttlSeconds: Long) {
        val key = "$REFRESH_TOKEN_KEY_PREFIX${userId.value}"
        redisTemplate.opsForValue().set(key, refreshToken, ttlSeconds, TimeUnit.SECONDS)
    }

    override fun getRefreshToken(userId: UserId): String? {
        val key = "$REFRESH_TOKEN_KEY_PREFIX${userId.value}"
        return redisTemplate.opsForValue().get(key)
    }

    override fun removeRefreshToken(userId: UserId) {
        val key = "$REFRESH_TOKEN_KEY_PREFIX${userId.value}"
        redisTemplate.delete(key)
    }

    override fun blacklistAccessToken(jti: String, ttlSeconds: Long) {
        val key = "$TOKEN_BLACKLIST_KEY_PREFIX$jti"
        redisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS)
    }

    override fun isAccessTokenBlacklisted(jti: String): Boolean {
        val key = "$TOKEN_BLACKLIST_KEY_PREFIX$jti"
        return redisTemplate.hasKey(key)
    }
}
