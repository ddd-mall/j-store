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

import com.jstore.user.domain.identity.*
import com.jstore.user.domain.useraccount.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URI
import java.net.URLEncoder
import org.springframework.http.*
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.HtmlUtils

internal fun csrf(request: HttpServletRequest): String {
  val token = request.getAttribute(CsrfToken::class.java.name) as CsrfToken
  return "<input type=hidden name=\"${escape(token.parameterName)}\" value=\"${escape(token.token)}\">"
}

internal fun scopeLabel(scope: String) =
    when (scope) {
      "openid" -> "账号标识"
      "profile" -> "昵称"
      "phone" -> "已验证手机号"
      "offline_access" -> "保持登录"
      else -> scope
    }

internal fun failure() =
    page("登录未完成", "验证失败、流程已过期或绑定发生冲突，请重新登录。<p><a href=/login>返回登录</a></p>", 400)

internal fun page(title: String, content: String, status: Int = 200): ResponseEntity<String> =
    ResponseEntity.status(status)
        .contentType(MediaType("text", "html", Charsets.UTF_8))
        .header("Cache-Control", "no-store")
        .header("Referrer-Policy", "no-referrer")
        .header(
            "Content-Security-Policy",
            "default-src 'none'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'",
        )
        .body(
            "<!doctype html><html lang=zh-CN><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\"><title>${escape(title)}</title><body><main><h1>${escape(title)}</h1>$content</main></body></html>"
        )

internal fun escape(value: String) = HtmlUtils.htmlEscape(value)

internal fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

internal fun authorizationContinuation(
    request: HttpServletRequest,
    response: HttpServletResponse,
    properties: OidcProperties,
): String {
  val saved = HttpSessionRequestCache().getRequest(request, response) ?: return "/"
  val uri = URI(saved.redirectUrl)
  return if (uri.path == "/oauth2/authorize")
      properties.issuer + uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
  else "/"
}
