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
package com.jstore.user.oidc

import java.security.MessageDigest
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.server.authorization.*
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Protocol credentials are persisted only as digests. Redemption callers must own a transaction.
 */
class DigestAuthorizationService(
    private val jdbc: JdbcOperations,
    clients: RegisteredClientRepository,
    private val active: (OAuth2Authorization) -> Boolean = { true },
) : OAuth2AuthorizationService {
    private val delegate =
        JdbcOAuth2AuthorizationService(jdbc, clients).apply {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            mapper.registerModules(
                org.springframework.security.jackson2.SecurityJackson2Modules.getModules(
                    javaClass.classLoader
                )
            )
            mapper.registerModule(
                org.springframework.security.oauth2.server.authorization.jackson2
                    .OAuth2AuthorizationServerJackson2Module()
            )
            // Session epochs and auth_time are immutable scalars, not arbitrary polymorphic types.
            mapper.addMixIn(java.lang.Long::class.java, LongScalar::class.java)
            setAuthorizationRowMapper(
                JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(clients).apply {
                    setObjectMapper(mapper)
                }
            )
            setAuthorizationParametersMapper(
                JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper().apply {
                    setObjectMapper(mapper)
                }
            )
        }

    @com.fasterxml.jackson.annotation.JsonTypeInfo(
        use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.CLASS
    )
    private abstract class LongScalar

    override fun save(authorization: OAuth2Authorization) {
        requireTransaction()
        val builder = OAuth2Authorization.from(authorization)
        authorization.getToken(OAuth2AuthorizationCode::class.java)?.let { entry ->
            builder.token(
                OAuth2AuthorizationCode(
                    digest(entry.token.tokenValue),
                    entry.token.issuedAt,
                    entry.token.expiresAt,
                )
            ) {
                it.putAll(entry.metadata)
            }
        }
        authorization.refreshToken?.let { entry ->
            val hash = digest(entry.token.tokenValue)
            jdbc.update(
                "INSERT INTO oauth2_token_history(digest, authorization_id, expires_at) VALUES (?, ?, ?) ON CONFLICT (digest) DO NOTHING",
                hash,
                authorization.id,
                java.sql.Timestamp.from(requireNotNull(entry.token.expiresAt)),
            )
            builder.refreshToken(
                OAuth2RefreshToken(hash, entry.token.issuedAt, entry.token.expiresAt)
            )
            builder.token(OAuth2RefreshToken(hash, entry.token.issuedAt, entry.token.expiresAt)) {
                it.putAll(entry.metadata)
            }
        }
        delegate.save(builder.build())
    }

    override fun remove(authorization: OAuth2Authorization) {
        requireTransaction()
        delegate.remove(authorization)
        // History intentionally survives removal until expiry to prevent credential resurrection.
    }

    override fun findById(id: String): OAuth2Authorization? = delegate.findById(id)

    override fun findByToken(token: String, tokenType: OAuth2TokenType?): OAuth2Authorization? {
        if (tokenType?.value == "code") {
            requireTransaction()
            lockCode(hash(token))
            val auth =
                delegate.findByToken(hash(token), tokenType)?.takeIf(::accessible) ?: return null
            val entry = auth.getToken(OAuth2AuthorizationCode::class.java) ?: return null
            return OAuth2Authorization.from(auth)
                .token(
                    OAuth2AuthorizationCode(token, entry.token.issuedAt, entry.token.expiresAt)
                ) {
                    it.putAll(entry.metadata)
                }
                .build()
        }
        if (tokenType == OAuth2TokenType.REFRESH_TOKEN) return findRefresh(token)
        if (tokenType == null) {
            // Revocation may omit token_type_hint. Access/ID/state values are not refresh
            // credentials.
            return delegate.findByToken(token, null)?.takeIf(::accessible) ?: findRefresh(token)
        }
        return delegate.findByToken(token, tokenType)?.takeIf(::accessible)
    }

    private fun findRefresh(token: String): OAuth2Authorization? {
        requireTransaction()
        val hash = hash(token)
        val ids =
            jdbc.query(
                "SELECT a.id FROM oauth2_authorization a JOIN oauth2_token_history h ON h.authorization_id = a.id WHERE h.digest = ? AND h.expires_at > CURRENT_TIMESTAMP FOR UPDATE OF a",
                { rs, _ -> rs.getString(1) },
                hash,
            )
        val auth = ids.firstOrNull()?.let(delegate::findById)?.takeIf(::accessible) ?: return null
        val entry = auth.refreshToken ?: return null
        if (entry.token.tokenValue != hash) {
            // A spent refresh credential identifies the family even after rotation.
            delegate.remove(auth)
            return null
        }
        return OAuth2Authorization.from(auth)
            .token(OAuth2RefreshToken(token, entry.token.issuedAt, entry.token.expiresAt)) {
                it.putAll(entry.metadata)
            }
            .build()
    }

    private fun accessible(authorization: OAuth2Authorization): Boolean {
        val client =
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                .authentication
                as?
                org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken
        return (client?.registeredClient == null ||
            client.registeredClient!!.id == authorization.registeredClientId) &&
            active(authorization)
    }

    private fun lockCode(hash: String) {
        jdbc.query(
            "SELECT id FROM oauth2_authorization WHERE authorization_code_value = ? FOR UPDATE",
            { rs, _ -> rs.getString(1) },
            hash,
        )
    }

    private fun requireTransaction() {
        check(TransactionSynchronizationManager.isActualTransactionActive()) {
            "OAuth credential exchange requires a transaction"
        }
    }

    companion object {
        fun digest(value: String): String =
            if (value.startsWith("sha256:") && value.length == 71) value else hash(value)

        fun hash(value: String): String =
            "sha256:" +
                MessageDigest.getInstance("SHA-256")
                    .digest(value.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
