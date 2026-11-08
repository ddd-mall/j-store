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

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.authentication.annotation.SkipLogin
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Success
import com.jstore.user.domain.identity.*
import com.jstore.user.domain.useraccount.*
import com.jstore.user.service.ExternalAccountBindingService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URI
import java.time.Clock
import org.springframework.http.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Controller
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.*

@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
    type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET
)
@Controller
class WechatLoginController(
    private val properties: OidcProperties,
    private val intents: RedisLoginIntentStore,
    private val wechat: WechatIdentityGateway,
    private val bindings: ExternalIdentityBindingRepository,
    private val binding: ExternalAccountBindingService,
    private val phones: PhoneVerificationGateway,
    private val sender: PhoneVerificationCodeSender,
    private val tokens: AccountSessionStore,
    private val guard: OidcSessionGuard,
    private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate,
    manager: PlatformTransactionManager,
    private val clock: Clock,
) {
  private val transaction = TransactionTemplate(manager)

  @SkipLogin
  @GetMapping("/login/wechat")
  fun start(request: HttpServletRequest, response: HttpServletResponse): String {
    if (!properties.wechat.enabled) return "redirect:/login?error"
    val state =
        intents.create(
            request.getSession(true).id,
            mapper.writeValueAsString(
                mapOf(
                    "appId" to properties.wechat.appId,
                    "target" to authorizationContinuation(request, response, properties),
                )
            ),
            300,
        )
    return "redirect:https://open.weixin.qq.com/connect/qrconnect?appid=${encode(properties.wechat.appId)}&redirect_uri=${encode(properties.wechat.callbackUri)}&response_type=code&scope=snsapi_login&state=$state#wechat_redirect"
  }

  @SkipLogin
  @GetMapping("/login/wechat/callback")
  fun callback(
      request: HttpServletRequest,
      response: HttpServletResponse,
      @RequestParam(required = false) code: String?,
      @RequestParam(required = false) state: String?,
  ): String {
    val session = request.getSession(false) ?: return "redirect:/login?error"
    if (!properties.wechat.enabled || state == null) return "redirect:/login?error"
    val intent =
        intents.consume(state, session.id)?.let(mapper::readTree) ?: return "redirect:/login?error"
    if (intent.path("appId").asText() != properties.wechat.appId) return "redirect:/login?error"
    val target = intent.path("target").asText()
    if (!validTarget(target)) return "redirect:/login?error"
    val verified = code?.let(wechat::exchange) ?: return "redirect:/login?error"
    val bound = bindings.findByIdentity(verified.identity)
    if (bound != null) return establish(bound.userId, target, request, response)
    val payload =
        mapper.writeValueAsString(
            mapOf(
                "appId" to verified.identity.appId,
                "subject" to verified.identity.subject,
                "unionId" to verified.unionId,
                "target" to target,
            )
        )
    session.setAttribute("wechat-onboarding", intents.create(session.id, payload, 600))
    return "redirect:/login/wechat/onboarding"
  }

  @SkipLogin
  @GetMapping("/login/wechat/onboarding")
  @ResponseBody
  fun onboarding(request: HttpServletRequest): ResponseEntity<String> {
    if (pending(request) == null) return failure()
    return page(
        "验证手机号并绑定账号",
        """
            <p>已有账号将绑定到已验证的手机号；新账号需填写昵称和密码。</p>
            <form method="post" action="/login/wechat/onboarding">${csrf(request)}
            <input type=hidden name=action value=send><label>手机号<input name=phone type=tel required></label><button>发送验证码</button></form>
            <form method="post" action="/login/wechat/onboarding">${csrf(request)}<input type=hidden name=action value=bind>
            <label>手机号<input name=phone type=tel required></label><input type=hidden name=challengeId value="${escape(request.getSession(false)?.getAttribute("wechat-challenge") as? String ?: "")}">
            <label>验证码<input name=code inputmode=numeric required></label><label>新账号昵称<input name=nickname maxlength=100></label>
            <label>新账号密码<input name=password type=password autocomplete=new-password></label>
            <label><input type=checkbox name=confirm value=true required>确认将此微信绑定到该手机号对应的账号</label><button>验证并继续</button></form>
        """
            .trimIndent(),
    )
  }

  @SkipLogin
  @PostMapping("/login/wechat/onboarding")
  @ResponseBody
  fun complete(
      request: HttpServletRequest,
      response: HttpServletResponse,
  ): ResponseEntity<String> {
    val pending = pending(request) ?: return failure()
    val session = request.getSession(false) ?: return failure()
    val id = session.getAttribute("wechat-onboarding") as? String ?: return failure()
    val phone =
        try {
          PhoneNumber(request.getParameter("phone").orEmpty())
        } catch (_: IllegalArgumentException) {
          return failure()
        }
    if (request.getParameter("action") == "send") {
      val challenge =
          phones.createChallenge(phone, "wechat-binding", id)
              ?: return page("请稍后重试", "验证码请求过于频繁。", 429)
      sender.send(phone, challenge.code)
      session.setAttribute("wechat-challenge", challenge.challenge.challengeId)
      return onboarding(request)
    }
    if (request.getParameter("action") != "bind" || request.getParameter("confirm") != "true")
        return failure()
    if (intents.consume(id, session.id) == null) return failure()
    session.removeAttribute("wechat-onboarding")
    return try {
      val result = transaction.execute {
        jdbc.query(
            "SELECT id FROM user_accounts WHERE phone_number = ? FOR UPDATE",
            { rs, _ -> rs.getLong(1) },
            phone.value,
        )
        binding.bind(
            VerifiedWechatIdentity(
                ExternalIdentity(
                    "wechat",
                    properties.wechat.appId,
                    pending.path("subject").asText(),
                ),
                pending.path("unionId").takeUnless { it.isNull }?.asText(),
            ),
            id,
            phone,
            PhoneVerificationProof(
                request.getParameter("challengeId").orEmpty(),
                request.getParameter("code").orEmpty(),
            ),
            request.getParameter("nickname").orEmpty(),
            request.getParameter("password").orEmpty(),
        )
      }
      if (result !is Success) return failure()
      val target =
          establish(result.value, pending.path("target").asText(), request, response)
              .removePrefix("redirect:")
      ResponseEntity.status(302).location(URI(target)).build()
    } catch (_: Exception) {
      failure()
    }
  }

  private fun pending(request: HttpServletRequest): com.fasterxml.jackson.databind.JsonNode? {
    if (!properties.wechat.enabled) return null
    val session = request.getSession(false) ?: return null
    val id = session.getAttribute("wechat-onboarding") as? String ?: return null
    return intents.read(id, session.id)?.let(mapper::readTree)?.takeIf {
      it.path("appId").asText() == properties.wechat.appId &&
          validTarget(it.path("target").asText())
    }
  }

  private fun validTarget(target: String) =
      target == "/" || target.startsWith(properties.issuer + "/oauth2/authorize?")

  private fun establish(
      userId: UserId,
      target: String,
      request: HttpServletRequest,
      response: HttpServletResponse,
  ): String {
    val authentication =
        LocalAccountAuthenticationProvider.createAuthentication(userId, tokens, clock)
    if (!guard.authenticationActive(authentication)) {
      guard.revoke(authentication)
      return "redirect:/login?error"
    }
    request.changeSessionId()
    org.springframework.security.web.csrf
        .HttpSessionCsrfTokenRepository()
        .saveToken(null, request, response)
    val context =
        SecurityContextHolder.createEmptyContext().apply {
          this.authentication = authentication
        }
    SecurityContextHolder.setContext(context)
    HttpSessionSecurityContextRepository().saveContext(context, request, response)
    return "redirect:$target"
  }
}
