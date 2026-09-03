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
package com.jstore.authentication.principal

data class AuthenticatedSession(val sessionId: String, val sessionEpoch: Long) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(sessionEpoch >= 0) { "sessionEpoch must not be negative" }
    }
}

/** Issuer-local account subject. The same numeric value may exist in another domain. */
data class AuthenticatedAccountId(val value: Long) {
    init {
        require(value > 0) { "accountId must be positive" }
    }
}

/** An account authenticated within one issuer/domain; [accountId] is not globally unique. */
data class AuthenticatedPrincipal(
    val authenticationDomain: String,
    val accountId: AuthenticatedAccountId,
    val session: AuthenticatedSession? = null,
) {
    init {
        require(authenticationDomain.isNotBlank()) { "authenticationDomain must not be blank" }
        require(authenticationDomain.length <= 255) { "authenticationDomain is too long" }
    }
}

/** Verifies an access token and maps its subject to the current authentication domain. */
fun interface AccessTokenVerifier {
    fun verifyAccessToken(token: String): AuthenticatedPrincipal?
}

/** Optional session-revocation port implemented by the authentication provider. */
fun interface AuthenticatedSessionStore {
    fun isSessionActive(
        accountId: AuthenticatedAccountId,
        sessionId: String,
        sessionEpoch: Long,
    ): Boolean
}
