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
package com.jstore.user

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.user.oidc.HttpWechatIdentityGateway
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import kotlin.test.*
import org.junit.jupiter.api.Test

class WechatIdentityGatewayTest {
  @Test
  fun `exchange checks userinfo identity and discards provider tokens`() {
    withServer("openid", "openid") { gateway ->
      val result = assertNotNull(gateway.exchange("one-time-code"))
      assertEquals("openid", result.identity.subject)
      assertFalse(result.toString().contains("provider-secret-token"))
    }
  }

  @Test
  fun `userinfo mismatch fails closed`() =
      withServer("openid", "other") { gateway -> assertNull(gateway.exchange("code")) }

  private fun withServer(
      tokenSubject: String,
      profileSubject: String,
      block: (HttpWechatIdentityGateway) -> Unit,
  ) {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/sns/oauth2/access_token") { exchange ->
      val body =
          """{"openid":"$tokenSubject","access_token":"provider-secret-token","scope":"snsapi_login"}"""
              .toByteArray()
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    server.createContext("/sns/userinfo") { exchange ->
      val body = """{"openid":"$profileSubject"}""".toByteArray()
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    server.start()
    try {
      block(
          HttpWechatIdentityGateway(
              "app",
              "test-secret",
              ObjectMapper(),
              URI("http://127.0.0.1:${server.address.port}"),
          )
      )
    } finally {
      server.stop(0)
    }
  }
}
