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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.user.domain.identity.*
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class HttpWechatIdentityGateway(
    private val appId: String,
    private val secret: String,
    private val mapper: ObjectMapper,
    private val baseUri: URI = URI("https://api.weixin.qq.com"),
    private val requestTimeout: Duration = Duration.ofSeconds(5),
) : WechatIdentityGateway {
  private val client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build()

  override fun exchange(code: String): VerifiedWechatIdentity? {
    if (code.isBlank() || code.length > 512) return null
    return try {
      val token =
          get(
              "/sns/oauth2/access_token",
              mapOf(
                  "appid" to appId,
                  "secret" to secret,
                  "code" to code,
                  "grant_type" to "authorization_code",
              ),
          ) ?: return null
      val subject = token.path("openid").asText()
      val access = token.path("access_token").asText()
      if (
          subject.isBlank() ||
              access.isBlank() ||
              "snsapi_login" !in token.path("scope").asText().split(',')
      )
          return null
      val profile =
          get("/sns/userinfo", mapOf("access_token" to access, "openid" to subject)) ?: return null
      if (profile.path("openid").asText() != subject) return null
      val union = token.path("unionid").asText().takeIf(String::isNotBlank)
      val profileUnion = profile.path("unionid").asText().takeIf(String::isNotBlank)
      if (union != null && profileUnion != null && union != profileUnion) return null
      VerifiedWechatIdentity(
          ExternalIdentity("wechat", appId, subject),
          profileUnion ?: union,
      )
    } catch (_: InterruptedException) {
      Thread.currentThread().interrupt()
      null
    } catch (_: Exception) {
      null
    }
  }

  private fun get(path: String, params: Map<String, String>): JsonNode? {
    val query = params.entries.joinToString("&") { encode(it.key) + "=" + encode(it.value) }
    val request =
        HttpRequest.newBuilder(baseUri.resolve("$path?$query"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
    val pending =
        client.sendAsync(
            request,
            HttpResponse.BodyHandler { _ ->
              HttpResponse.BodySubscribers.limiting(
                  HttpResponse.BodySubscribers.ofByteArray(),
                  65536,
              )
            },
        )
    val response =
        try {
          pending.get(requestTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (failure: Exception) {
          pending.cancel(true)
          throw failure
        }
    if (response.statusCode() != 200) return null
    val node = mapper.readTree(response.body()) ?: return null
    if (!node.isObject || node.has("errcode")) return null
    return node
  }

  private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)
}
