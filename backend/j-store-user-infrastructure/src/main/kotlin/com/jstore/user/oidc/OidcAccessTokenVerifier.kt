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

import com.jstore.authentication.principal.*
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization

class OidcAccessTokenVerifier(
    private val decoder: JwtDecoder,
    private val authorizations: DigestAuthorizationService,
    private val issuer: String,
    private val audience: String,
    private val active: (OAuth2Authorization) -> Boolean,
) : AccessTokenVerifier {
  override fun verifyAccessToken(token: String): AuthenticatedPrincipal? =
      try {
        val jwt = decoder.decode(token)
        val subject = jwt.subject.toLongOrNull()?.takeIf { it > 0 }
        val sid = jwt.getClaimAsString("sid")
        val epoch = (jwt.claims["sev"] as? Number)?.toLong()
        val grant = jwt.getClaimAsString("aid")?.let(authorizations::findById)
        if (
            jwt.headers["alg"] != "RS256" ||
                jwt.claims.containsKey("cnf") ||
                (jwt.headers["kid"] as? String).isNullOrBlank() ||
                jwt.issuer.toString() != issuer ||
                audience !in jwt.audience ||
                jwt.getClaimAsString("token_use") != "access" ||
                jwt.id.isNullOrBlank() ||
                jwt.expiresAt == null ||
                jwt.issuedAt == null ||
                subject == null ||
                sid.isNullOrBlank() ||
                epoch == null ||
                epoch < 0 ||
                grant == null ||
                grant.principalName != subject.toString() ||
                grant.accessToken?.isActive != true ||
                grant.accessToken?.token?.tokenValue != token ||
                !active(grant)
        )
            null
        else
            AuthenticatedPrincipal(
                issuer,
                AuthenticatedAccountId(subject),
                AuthenticatedSession(sid, epoch),
            )
      } catch (_: Exception) {
        null
      }
}
