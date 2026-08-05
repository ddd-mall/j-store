package com.jstore.user.filter

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.user.domain.useraccount.TokenProvider
import com.jstore.user.domain.useraccount.TokenStore
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val tokenProvider: TokenProvider,
    private val tokenStore: TokenStore,
) : OncePerRequestFilter() {

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        const val USER_ID_ATTRIBUTE = "userId"

        private val WHITELIST_PATHS =
            setOf(
                "/api/users/register",
                "/api/users/login",
                "/api/users/refresh-token",
            )
    }

    private val objectMapper = ObjectMapper()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // Skip whitelist paths
        if (WHITELIST_PATHS.contains(request.requestURI)) {
            filterChain.doFilter(request, response)
            return
        }

        val authHeader = request.getHeader(AUTHORIZATION_HEADER)
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "令牌无效", "User.Token.Invalid")
            return
        }

        val token = authHeader.substring(BEARER_PREFIX.length)

        // Parse and validate token
        val userId = tokenProvider.parseAccessToken(token)
        if (userId == null) {
            writeUnauthorized(response, "令牌无效", "User.Token.Invalid")
            return
        }

        // Check blacklist
        val jti = tokenProvider.getAccessTokenJti(token)
        if (jti != null && tokenStore.isAccessTokenBlacklisted(jti)) {
            writeUnauthorized(response, "令牌已被吊销", "User.Token.Invalid")
            return
        }

        // Set userId in request attribute
        request.setAttribute(USER_ID_ATTRIBUTE, userId.value)
        filterChain.doFilter(request, response)
    }

    private fun writeUnauthorized(
        response: HttpServletResponse,
        message: String,
        errorCode: String,
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        val body = mapOf("message" to message, "errorCode" to errorCode)
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
