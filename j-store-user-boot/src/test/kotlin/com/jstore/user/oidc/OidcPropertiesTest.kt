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

import kotlin.test.*
import org.junit.jupiter.api.Test

class OidcPropertiesTest {
  @Test
  fun `issuer cannot be inferred or use insecure external host`() {
    assertFailsWith<IllegalArgumentException> { OidcProperties().validate(false) }
    assertFailsWith<IllegalArgumentException> {
      OidcProperties(issuer = "http://identity.example").validate(false)
    }
  }

  @Test
  fun `wildcard redirect and public refresh client configuration are rejected`() {
    assertFailsWith<IllegalArgumentException> {
      OidcClientProperties(id = "spa", redirects = listOf("https://*.example/callback")).validate()
    }
    assertFailsWith<IllegalArgumentException> {
      OidcClientProperties(
              id = "spa",
              redirects = listOf("https://app.example/callback"),
              scopes = setOf("openid", "offline_access"),
          )
          .validate()
    }
  }
}
