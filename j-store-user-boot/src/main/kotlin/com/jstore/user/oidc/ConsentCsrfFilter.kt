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
import java.security.MessageDigest
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler
import org.springframework.web.filter.OncePerRequestFilter

/** SAS protocol CSRF exemptions must not exempt a browser consent POST. */
class ConsentCsrfFilter : OncePerRequestFilter() {
  override fun shouldNotFilter(request: HttpServletRequest) =
      request.method != "POST" || request.requestURI != request.contextPath + "/oauth2/authorize"

  override fun doFilterInternal(
      request: HttpServletRequest,
      response: HttpServletResponse,
      chain: FilterChain,
  ) {
    val expected = HttpSessionCsrfTokenRepository().loadToken(request)
    val actual = expected?.let {
      XorCsrfTokenRequestAttributeHandler().resolveCsrfTokenValue(request, it)
    }
    if (
        expected == null ||
            actual == null ||
            !MessageDigest.isEqual(expected.token.toByteArray(), actual.toByteArray())
    ) {
      response.sendError(403)
      return
    }
    chain.doFilter(request, response)
  }
}
