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

import com.jstore.authentication.annotation.SkipLogin
import com.jstore.user.domain.identity.*
import com.jstore.user.domain.useraccount.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.*
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
    type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET
)
@Controller
class LoginPageController(private val properties: OidcProperties) {
    @SkipLogin
    @GetMapping("/login")
    @ResponseBody
    fun login(request: HttpServletRequest): ResponseEntity<String> =
        page(
            "登录",
            """
        <form method="post" action="/login">${csrf(request)}
        <label>手机号<input name="username" type="tel" required autocomplete="username"></label>
        <label>密码<input name="password" type="password" required autocomplete="current-password"></label>
        <button>登录</button></form>
        ${if (properties.wechat.enabled) "<p><a href=\"/login/wechat\">微信扫码登录</a></p>" else ""}
        ${if (request.getParameter("error") != null) "<p role=\"alert\">登录失败，请检查凭证后重试。</p>" else ""}
    """
                .trimIndent(),
        )
}
