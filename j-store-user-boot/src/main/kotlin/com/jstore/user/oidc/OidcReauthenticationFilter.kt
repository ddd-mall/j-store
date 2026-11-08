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
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Clock
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.web.filter.OncePerRequestFilter

/** Force a fresh authentication once, then allow the same saved authorization request to resume. */
class OidcReauthenticationFilter(
    private val clients: RegisteredClientRepository,
    private val clock: Clock,
) : OncePerRequestFilter() {
  override fun doFilterInternal(
      request: HttpServletRequest,
      response: HttpServletResponse,
      chain: FilterChain,
  ) {
    if (
        request.method == "GET" && request.requestURI == request.contextPath + "/oauth2/authorize"
    ) {
      val prompt = request.getParameter("prompt").orEmpty().split(' ')
      val maxAge = request.getParameter("max_age")?.toLongOrNull()
      val authentication = SecurityContextHolder.getContext().authentication
      val details = authentication?.details as? Map<*, *>
      val sid = details?.get("sid") as? String ?: ""
      val authTime = (details?.get("auth_time") as? Number)?.toLong() ?: 0
      val client = request.getParameter("client_id")?.let(clients::findByClientId)
      val validClient =
          client != null && request.getParameter("redirect_uri") in client.redirectUris
      if (
          validClient &&
              ("login" in prompt ||
                  (maxAge != null &&
                      maxAge >= 0 &&
                      (maxAge == 0L || clock.instant().epochSecond - authTime > maxAge)))
      ) {
        val fingerprint =
            DigestAuthorizationService.hash(
                request.parameterMap
                    .toSortedMap()
                    .filterKeys { it != "continue" }
                    .entries
                    .joinToString("&") { entry ->
                      entry.key +
                          "=" +
                          entry.value.joinToString(",") {
                            java.net.URLEncoder.encode(it, Charsets.UTF_8)
                          }
                    }
            )
        val session = request.getSession(true)
        val pending = session.getAttribute("oidc:reauth") as? Map<*, *>
        if (pending?.get("request") == fingerprint && sid.isNotBlank() && pending["sid"] != sid) {
          session.removeAttribute("oidc:reauth")
        } else {
          session.setAttribute(
              "oidc:reauth",
              hashMapOf("request" to fingerprint, "sid" to sid),
          )
          SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext())
        }
      }
    }
    chain.doFilter(request, response)
  }
}
