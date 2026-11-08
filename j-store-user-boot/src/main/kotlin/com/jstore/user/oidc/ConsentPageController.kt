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
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
    type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET
)
@Controller
class ConsentPageController(
    private val clients: RegisteredClientRepository,
    private val authorizations: DigestAuthorizationService,
) {
    @SkipLogin
    @GetMapping("/consent")
    @ResponseBody
    fun consent(
        request: HttpServletRequest,
        @RequestParam("client_id") clientId: String,
        @RequestParam state: String,
    ): ResponseEntity<String> {
        val client = clients.findByClientId(clientId) ?: return failure()
        val auth = authorizations.findByToken(state, OAuth2TokenType("state")) ?: return failure()
        if (
            auth.registeredClientId != client.id ||
                auth.principalName != SecurityContextHolder.getContext().authentication.name
        )
            return failure()
        val original =
            auth.getAttribute<OAuth2AuthorizationRequest>(
                OAuth2AuthorizationRequest::class.java.name
            ) ?: return failure()
        val hidden =
            csrf(request) +
                "<input type=hidden name=client_id value=\"${escape(clientId)}\"><input type=hidden name=state value=\"${escape(state)}\">"
        val scopes =
            original.scopes.joinToString("") { scope ->
                "<label><input type=checkbox name=scope value=\"${escape(scope)}\" checked>${escape(scopeLabel(scope))}</label>"
            }
        return page(
            "授权确认",
            "<p>${escape(client.clientName)} 请求访问以下资料：</p><form method=post action=/oauth2/authorize>$hidden$scopes<button>同意</button></form><form method=post action=/oauth2/authorize>$hidden<button>拒绝</button></form>",
        )
    }
}
