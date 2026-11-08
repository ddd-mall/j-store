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

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.security.oauth2.core.*
import org.springframework.security.oauth2.server.authorization.*
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.transaction.support.TransactionTemplate

class DigestAuthorizationServiceTest {
  private val client =
      RegisteredClient.withId("client")
          .clientId("web")
          .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
          .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
          .redirectUri("https://client.example/callback")
          .scope("openid")
          .build()

  private fun withStore(
      block: (DigestAuthorizationService, JdbcTemplate, TransactionTemplate) -> Unit
  ) {
    EmbeddedPostgres.builder().start().use { pg ->
      val dataSource = pg.postgresDatabase
      val jdbc = JdbcTemplate(dataSource)
      val schema =
          javaClass
              .getResourceAsStream(
                  "/org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql"
              )!!
              .bufferedReader()
              .readText()
              .replace("blob DEFAULT", "text DEFAULT")
              .replace("timestamp DEFAULT", "timestamptz DEFAULT")
      jdbc.execute(schema)
      jdbc.execute(
          "CREATE TABLE oauth2_token_history (digest varchar(80) PRIMARY KEY, authorization_id varchar(100) NOT NULL, expires_at timestamptz NOT NULL)"
      )
      val store = DigestAuthorizationService(jdbc, InMemoryRegisteredClientRepository(client))
      block(store, jdbc, TransactionTemplate(DataSourceTransactionManager(dataSource)))
    }
  }

  private fun authorization(): OAuth2Authorization {
    val now = Instant.now()
    return OAuth2Authorization.withRegisteredClient(client)
        .id("grant")
        .principalName("42")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizedScopes(setOf("openid"))
        .token(OAuth2AuthorizationCode("secret-code", now, now.plusSeconds(60)))
        .accessToken(
            OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access",
                now,
                now.plusSeconds(900),
            )
        )
        .refreshToken(OAuth2RefreshToken("secret-refresh", now, now.plusSeconds(604800)))
        .build()
  }

  @Test
  fun `database never stores reversible code or refresh credentials`() =
      withStore { store, jdbc, tx ->
        tx.executeWithoutResult { store.save(authorization()) }
        val row = jdbc.queryForMap("SELECT * FROM oauth2_authorization")
        assertFalse(
            row.values.any {
              it?.toString()?.contains("secret-code") == true ||
                  it?.toString()?.contains("secret-refresh") == true
            }
        )
        tx.executeWithoutResult {
          assertNull(
              store.findByToken(
                  DigestAuthorizationService.hash("secret-refresh"),
                  OAuth2TokenType.REFRESH_TOKEN,
              )
          )
          assertEquals(
              "secret-code",
              store
                  .findByToken("secret-code", OAuth2TokenType("code"))!!
                  .getToken(OAuth2AuthorizationCode::class.java)!!
                  .token
                  .tokenValue,
          )
          assertEquals(
              "secret-refresh",
              store
                  .findByToken("secret-refresh", OAuth2TokenType.REFRESH_TOKEN)!!
                  .refreshToken!!
                  .token
                  .tokenValue,
          )
        }
      }

  @Test
  fun `code consumption remains single use across concurrent transactions`() =
      withStore { store, jdbc, tx ->
        tx.executeWithoutResult { store.save(authorization()) }
        val pool = Executors.newFixedThreadPool(8)
        try {
          val results =
              pool
                  .invokeAll(
                      (1..8).map {
                        Callable {
                          tx.execute {
                            val auth =
                                store.findByToken(
                                    "secret-code",
                                    OAuth2TokenType("code"),
                                )!!
                            val code = auth.getToken(OAuth2AuthorizationCode::class.java)!!
                            if (!code.isActive) false
                            else {
                              store.save(
                                  OAuth2Authorization.from(auth)
                                      .token(code.token) {
                                        it[OAuth2Authorization.Token.INVALIDATED_METADATA_NAME] =
                                            true
                                      }
                                      .build()
                              )
                              true
                            }
                          }
                        }
                      }
                  )
                  .map { it.get() }
          assertEquals(
              1,
              results.count { it == true },
              jdbc
                  .queryForMap("SELECT authorization_code_metadata FROM oauth2_authorization")
                  .toString(),
          )
        } finally {
          pool.shutdownNow()
        }
      }

  @Test
  fun `old refresh replay revokes the grant and its access token`() = withStore { store, _, tx ->
    tx.executeWithoutResult {
      store.save(authorization())
      val auth = store.findByToken("secret-refresh", OAuth2TokenType.REFRESH_TOKEN)!!
      store.save(
          OAuth2Authorization.from(auth)
              .refreshToken(
                  OAuth2RefreshToken(
                      "replacement",
                      Instant.now(),
                      Instant.now().plusSeconds(600),
                  )
              )
              .build()
      )
    }
    tx.executeWithoutResult {
      assertNull(store.findByToken("secret-refresh", OAuth2TokenType.REFRESH_TOKEN))
    }
    tx.executeWithoutResult {
      assertNull(store.findByToken("replacement", OAuth2TokenType.REFRESH_TOKEN))
      assertNull(store.findByToken("access", OAuth2TokenType.ACCESS_TOKEN))
    }
  }

  @Test
  fun `database binding constraints prevent identity reassignment and silent replacement`() =
      withStore { _, jdbc, tx ->
        jdbc.execute("CREATE TABLE user_accounts(id bigint PRIMARY KEY)")
        jdbc.update("INSERT INTO user_accounts VALUES (1), (2)")
        val schema =
            java.nio.file.Files.readString(
                java.nio.file.Path.of(
                    "../j-store-boot/src/main/resources/db/migration/V20260507__baseline_j_store_boot_schema.sql"
                )
            )
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS user_external_identities" +
                schema.substringAfter("CREATE TABLE IF NOT EXISTS user_external_identities")
        )
        val repository = JdbcExternalIdentityBindingRepository(jdbc)
        val identity = com.jstore.user.domain.identity.ExternalIdentity("wechat", "app", "wx-one")
        val binding =
            com.jstore.user.domain.identity.ExternalIdentityBinding(
                com.jstore.user.domain.identity.ExternalIdentityBindingId(),
                identity,
                com.jstore.user.domain.useraccount.UserId(1),
            )
        tx.executeWithoutResult { repository.add(binding) }
        assertEquals(binding.userId, repository.findByIdentity(identity)?.userId)
        assertFailsWith<org.springframework.dao.DataIntegrityViolationException> {
          tx.executeWithoutResult {
            repository.add(
                com.jstore.user.domain.identity.ExternalIdentityBinding(
                    com.jstore.user.domain.identity.ExternalIdentityBindingId(),
                    identity,
                    com.jstore.user.domain.useraccount.UserId(2),
                )
            )
          }
        }
        assertFailsWith<org.springframework.dao.DataIntegrityViolationException> {
          tx.executeWithoutResult {
            repository.add(
                com.jstore.user.domain.identity.ExternalIdentityBinding(
                    com.jstore.user.domain.identity.ExternalIdentityBindingId(),
                    com.jstore.user.domain.identity.ExternalIdentity(
                        "wechat",
                        "app",
                        "wx-two",
                    ),
                    binding.userId,
                )
            )
          }
        }
        assertEquals(binding.userId, repository.findByIdentity(identity)?.userId)
      }
}
