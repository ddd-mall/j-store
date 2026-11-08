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

import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.*
import com.jstore.user.service.AccountCredentialsService
import java.time.Clock
import org.springframework.security.authentication.*
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils

class LocalAccountAuthenticationProvider(
    private val credentials: AccountCredentialsService,
    private val tokens: AccountSessionStore,
    private val clock: Clock,
    private val jdbc: org.springframework.jdbc.core.JdbcTemplate,
    manager: org.springframework.transaction.PlatformTransactionManager,
) : AuthenticationProvider {
  private val transaction = org.springframework.transaction.support.TransactionTemplate(manager)

  override fun supports(type: Class<*>) =
      UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(type)

  override fun authenticate(authentication: Authentication): Authentication {
    val phone =
        try {
          PhoneNumber(authentication.name)
        } catch (_: IllegalArgumentException) {
          throw BadCredentialsException("Invalid credentials")
        }
    return requireNotNull(
        transaction.execute {
          // Serialize local password verification with password changes and account disable.
          jdbc.query(
              "SELECT id FROM user_accounts WHERE phone_number = ? FOR UPDATE",
              { rs, _ -> rs.getLong(1) },
              phone.value,
          )
          val result =
              credentials.authenticate(
                  phone,
                  authentication.credentials?.toString().orEmpty(),
              )
          if (result !is Success) throw BadCredentialsException("Invalid credentials")
          createAuthentication(result.value, tokens, clock)
        }
    )
  }

  companion object {
    fun createAuthentication(
        id: UserId,
        tokens: AccountSessionStore,
        clock: Clock,
    ): UsernamePasswordAuthenticationToken {
      val sid = RedisLoginIntentStore.randomId()
      val epoch = tokens.currentSessionEpoch(id)
      if (!tokens.createSession(id, sid, epoch, 604800))
          throw CredentialsExpiredException("Session changed; authenticate again")
      return UsernamePasswordAuthenticationToken.authenticated(
              id.value.toString(),
              null,
              AuthorityUtils.NO_AUTHORITIES,
          )
          .apply {
            details =
                hashMapOf<String, Any>(
                    "sid" to sid,
                    "sev" to epoch,
                    "auth_time" to clock.instant().epochSecond,
                )
          }
    }
  }
}
