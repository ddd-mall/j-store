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

import com.jstore.user.domain.useraccount.*
import java.security.Principal
import java.time.Clock
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization

class OidcSessionGuard(
    private val accounts: UserAccountRepository,
    private val tokens: AccountSessionStore,
    private val clock: Clock,
) {
  fun authenticationActive(authentication: Authentication): Boolean {
    val id = authentication.name.toLongOrNull()?.takeIf { it > 0 } ?: return false
    val details = authentication.details as? Map<*, *> ?: return false
    val sid = details["sid"] as? String ?: return false
    val epoch = (details["sev"] as? Number)?.toLong() ?: return false
    return accounts.findById(UserId(id))?.status == UserAccountStatus.ACTIVE &&
        tokens.isSessionActive(UserId(id), sid, epoch)
  }

  fun browserActive(authentication: Authentication): Boolean {
    val details = authentication.details as? Map<*, *> ?: return false
    val time = (details["auth_time"] as? Number)?.toLong() ?: return false
    return clock.instant().epochSecond - time in 0..43200 && authenticationActive(authentication)
  }

  fun authorizationActive(authorization: OAuth2Authorization): Boolean =
      authorization
          .getAttribute<Authentication>(Principal::class.java.name)
          ?.let(::authenticationActive) == true

  fun revoke(authentication: Authentication) {
    val details = authentication.details as? Map<*, *> ?: return
    val sid = details["sid"] as? String ?: return
    tokens.revokeSession(UserId(authentication.name.toLong()), sid)
  }

  fun userInfo(subject: String, scopes: Set<String>): Map<String, Any> {
    val account = requireNotNull(accounts.findById(UserId(subject.toLong())))
    return buildMap {
      put("sub", subject)
      if ("profile" in scopes) put("nickname", account.nickname.value)
      if ("phone" in scopes) {
        put("phone_number", account.phoneNumber.value)
        put("phone_number_verified", true)
      }
    }
  }
}
