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

import jakarta.servlet.FilterChain
import java.time.*
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.mock.web.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.*
import org.springframework.security.oauth2.server.authorization.client.*

class OidcReauthenticationFilterTest {
    @Test
    fun `prompt login forces fresh authentication once across saved request continuation`() {
        val client =
            RegisteredClient.withId("web")
                .clientId("web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example/callback")
                .build()
        val clients = InMemoryRegisteredClientRepository(client)
        val session = MockHttpSession()
        val filter = OidcReauthenticationFilter(clients, Clock.systemUTC())
        fun authentication(sid: String) =
            UsernamePasswordAuthenticationToken.authenticated(
                    "42",
                    null,
                    AuthorityUtils.NO_AUTHORITIES,
                )
                .apply { details = mapOf("sid" to sid, "auth_time" to Instant.now().epochSecond) }
        fun request() =
            MockHttpServletRequest("GET", "/oauth2/authorize").apply {
                this.session = session
                addParameter("client_id", "web")
                addParameter("redirect_uri", "https://client.example/callback")
                addParameter("prompt", "login")
            }
        try {
            SecurityContextHolder.getContext().authentication = authentication("old")
            filter.doFilter(
                request(),
                MockHttpServletResponse(),
                FilterChain { _, _ ->
                    assertNull(SecurityContextHolder.getContext().authentication)
                },
            )
            SecurityContextHolder.getContext().authentication = authentication("fresh")
            val resumed = request().apply { addParameter("continue", "") }
            filter.doFilter(
                resumed,
                MockHttpServletResponse(),
                FilterChain { _, _ ->
                    assertEquals("42", SecurityContextHolder.getContext().authentication.name)
                },
            )
        } finally {
            SecurityContextHolder.clearContext()
        }
    }
}
