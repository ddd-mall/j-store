package com.jstore.user.domain.useraccount

import java.time.LocalDateTime

data class AuthTokenPair(
    val accessToken: String,
    val accessTokenExpiresAt: LocalDateTime,
    val refreshToken: String,
    val refreshTokenExpiresAt: LocalDateTime,
)
